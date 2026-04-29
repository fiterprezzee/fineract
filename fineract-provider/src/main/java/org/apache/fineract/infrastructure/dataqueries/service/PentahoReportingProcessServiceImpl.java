/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.infrastructure.dataqueries.service;

import static org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection.toJdbcUrl;
import static org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection.toProtocol;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.ApiParameterHelper;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.database.DatabasePasswordEncryptor;
import org.apache.fineract.infrastructure.dataqueries.data.ReportExportType;
import org.apache.fineract.infrastructure.report.annotation.ReportService;
import org.apache.fineract.infrastructure.report.service.ReportingProcessService;
import org.apache.fineract.infrastructure.security.constants.TenantConstants;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.pentaho.reporting.engine.classic.core.*;
import org.pentaho.reporting.engine.classic.core.modules.misc.datafactory.sql.DriverConnectionProvider;
import org.pentaho.reporting.engine.classic.core.modules.misc.datafactory.sql.SQLReportDataFactory;
import org.pentaho.reporting.engine.classic.core.modules.output.pageable.pdf.PdfReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.csv.CSVReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.html.HtmlReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.xls.ExcelReportUtil;
import org.pentaho.reporting.engine.classic.core.parameters.ParameterDefinitionEntry;
import org.pentaho.reporting.libraries.resourceloader.Resource;
import org.pentaho.reporting.libraries.resourceloader.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@ReportService(type = "Pentaho")
@Slf4j
public class PentahoReportingProcessServiceImpl implements ReportingProcessService {

    private static final Logger logger = LoggerFactory.getLogger(PentahoReportingProcessServiceImpl.class);

    // Pentaho reports directory - configurable via environment variable or defaults to ~/.mifosx
    private final String mifosBaseDir;
    private final DatabasePasswordEncryptor databasePasswordEncryptor;

    private final PlatformSecurityContext context;
    private final DataSource tenantDataSource;

    private final FineractProperties fineractProperties;

    private final ApplicationContext applicationContext;

    private final ApplicationContext contextVar;

    @Autowired
    public PentahoReportingProcessServiceImpl(final PlatformSecurityContext context,
            final @Qualifier("hikariTenantDataSource") DataSource tenantDataSource, DatabasePasswordEncryptor databasePasswordEncryptor,
            FineractProperties fineractProperties, ApplicationContext applicationContext, ApplicationContext contextVar) {
        this.fineractProperties = fineractProperties;
        this.applicationContext = applicationContext;
        this.contextVar = contextVar;

        // Initialize Pentaho reports base directory from properties, fallback to env var, then default
        String pentahoReportsPath = null;

        // First try from FineractProperties (application.properties / environment)
        if (fineractProperties.getReport() != null && fineractProperties.getReport().getPentaho() != null
                && fineractProperties.getReport().getPentaho().getPath() != null
                && !fineractProperties.getReport().getPentaho().getPath().isEmpty()) {
            pentahoReportsPath = fineractProperties.getReport().getPentaho().getPath();
        }

        // Fallback to environment variable
        if (pentahoReportsPath == null || pentahoReportsPath.isEmpty()) {
            pentahoReportsPath = System.getenv("FINERACT_PENTAHO_REPORTS_PATH");
        }

        // Final fallback to default
        if (pentahoReportsPath == null || pentahoReportsPath.isEmpty()) {
            pentahoReportsPath = System.getProperty("user.home") + File.separator + ".mifosx";
        }

        this.mifosBaseDir = pentahoReportsPath;
        logger.info("Pentaho reports base directory: {}", this.mifosBaseDir);

        ClassicEngineBoot.getInstance().start();
        this.tenantDataSource = tenantDataSource;
        this.context = context;
        this.databasePasswordEncryptor = databasePasswordEncryptor;
    }

    private void checkPermissionForReport(String reportName) {
        // Ensure user is authenticated
        this.context.authenticatedUser();

        // Here you would implement specific permission checks based on report name
        // For example, check if the user has the specific permission to view this report
        // This is a placeholder for actual permission logic
        if (!hasPermissionForReport(reportName)) {
            log.info("no permission to read or load file");

        }
    }

    /**
     * Determines if the current user has permission to access the specified report
     *
     * @param reportName
     *            the name of the report
     * @return true if the user has permission, false otherwise
     */
    private boolean hasPermissionForReport(String reportName) {
        // Implement actual permission logic here
        // This could involve checking user roles, permissions, etc.
        // For now, returning true as a placeholder
        return true;
    }

    /**
     * Checks if the report file exists and is accessible
     *
     * @param reportPath
     *            the full path to the report file
     * @throws PlatformDataIntegrityException
     *             if the file doesn't exist or isn't accessible
     */
    private void checkReportFileExists(String reportPath) {
        Path path = Paths.get(reportPath);

        if (!Files.exists(path)) {
            throw new PlatformDataIntegrityException("error.msg.report.file.not.found", "Report file not found: " + reportPath, reportPath);
        }

        if (!Files.isReadable(path)) {
            throw new PlatformDataIntegrityException("error.msg.report.file.not.readable", "Report file is not readable: " + reportPath,
                    reportPath);
        }
    }

    @Override
    public Response processRequest(final String reportName, final MultivaluedMap<String, String> queryParams) {

        final var outputTypeParam = queryParams.getFirst("output-type");
        final var reportParams = getReportParams(queryParams);
        final var locale = ApiParameterHelper.extractLocale(queryParams);
        final var language = "en";

        var outputType = "HTML";
        if (StringUtils.isNotBlank(outputTypeParam)) {
            outputType = outputTypeParam;
        }

        if ((!outputType.equalsIgnoreCase("HTML") && !outputType.equalsIgnoreCase("PDF") && !outputType.equalsIgnoreCase("XLS")
                && !outputType.equalsIgnoreCase("XLSX") && !outputType.equalsIgnoreCase("CSV"))) {
            throw new PlatformDataIntegrityException("error.msg.invalid.outputType", "No matching Output Type: " + outputType);
        }

        log.info("Processing report: {} with output type: {} and locale: {}", reportName, outputType, locale);
        String reportPath;
        if (!"en".equalsIgnoreCase(Objects.toString(locale, "en"))) {
            reportPath = getReportPath() + reportName + "_" + locale.toString().toLowerCase() + ".prpt";
        } else {
            reportPath = getReportPath() + reportName + ".prpt";
        }

        // Check if report file exists and is accessible
        checkReportFileExists(reportPath);

        // Check if user has permission to access this report
        checkPermissionForReport(reportPath);

        var outPutInfo = "Report path: " + reportPath;
        logger.info("Report path: {}", outPutInfo);

        // load report definition
        final var manager = new ResourceManager();
        manager.registerDefaults();
        Resource res;

        try {
            res = manager.createDirectly(reportPath, MasterReport.class);
            final var masterReport = (MasterReport) res.getResource();

            // Override Data Connection Factory with the driver and url for all data factories
            // including those in subreports
            updateAllDataFactories(masterReport);

            final var reportEnvironment = (DefaultReportEnvironment) masterReport.getReportEnvironment();
            if (locale != null) {
                reportEnvironment.setLocale(locale);
            }

            addParametersToReport(masterReport, reportParams);

            final var baos = new ByteArrayOutputStream();

            if ("PDF".equalsIgnoreCase(outputType)) {
                PdfReportUtil.createPDF(masterReport, baos);
                return Response.ok().entity(baos.toByteArray()).type("application/pdf").build();

            } else if ("XLS".equalsIgnoreCase(outputType)) {
                ExcelReportUtil.createXLS(masterReport, baos);
                return Response.ok().entity(baos.toByteArray()).type("application/vnd.ms-excel")
                        .header("Content-Disposition", "attachment;filename=" + reportName.replaceAll(" ", "") + ".xls").build();

            } else if ("XLSX".equalsIgnoreCase(outputType)) {
                ExcelReportUtil.createXLSX(masterReport, baos);
                return Response.ok().entity(baos.toByteArray()).type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        .header("Content-Disposition", "attachment;filename=" + reportName.replaceAll(" ", "") + ".xlsx").build();

            } else if ("CSV".equalsIgnoreCase(outputType)) {
                CSVReportUtil.createCSV(masterReport, baos, "UTF-8");
                return Response.ok().entity(baos.toByteArray()).type("text/csv")
                        .header("Content-Disposition", "attachment;filename=" + reportName.replaceAll(" ", "") + ".csv").build();

            } else if ("HTML".equalsIgnoreCase(outputType)) {
                HtmlReportUtil.createStreamHTML(masterReport, baos);
                return Response.ok().entity(baos.toByteArray()).type("text/html").build();

            } else {
                throw new PlatformDataIntegrityException("error.msg.invalid.outputType", "No matching Output Type: " + outputType);

            }
        } catch (Throwable t) {
            logger.error("Pentaho failed", t);
            throw new PlatformDataIntegrityException("error.msg.reporting.error", "Pentaho report generation failed: " + t.getMessage());
        }
    }

    private void addParametersToReport(final MasterReport report, final Map<String, String> queryParams) {
        final var currentUser = this.context.authenticatedUser();
        try {
            final var rptParamValues = report.getParameterValues();
            final var paramsDefinition = report.getParameterDefinition();

            // only allow integer, long, date and string parameter types and assume all mandatory - could go more
            // detailed like Pawel did in Mifos later and could match incoming and Pentaho parameters better...
            // currently assuming they come in ok... and if not an error
            for (final ParameterDefinitionEntry paramDefEntry : paramsDefinition.getParameterDefinitions()) {
                final var paramName = paramDefEntry.getName();
                if ((!paramName.equals("tenantUrl") && (!paramName.equals("userhierarchy") && !paramName.equals("username")
                        && (!paramName.equals("password") && !paramName.equals("userid"))))) {

                    var outPutInfo2 = "paramName:" + paramName;
                    logger.debug("paramName: {}", outPutInfo2);

                    final var pValue = queryParams.get(paramName);
                    if (StringUtils.isBlank(pValue)) {
                        throw new PlatformDataIntegrityException("error.msg.reporting.error",
                                "Pentaho Parameter: " + paramName + " - not Provided");
                    }

                    final Class<?> clazz = paramDefEntry.getValueType();
                    var outPutInfo3 = "addParametersToReport(" + paramName + " : " + pValue + " : " + clazz.getCanonicalName() + ")";
                    logger.debug("outputInfo: {}", outPutInfo3);

                    if (clazz.getCanonicalName().equalsIgnoreCase("java.lang.Integer")) {
                        rptParamValues.put(paramName, Integer.parseInt(pValue));
                    } else if (clazz.getCanonicalName().equalsIgnoreCase("java.lang.Long")) {
                        rptParamValues.put(paramName, Long.parseLong(pValue));
                    } else if (clazz.getCanonicalName().equalsIgnoreCase("java.sql.Date")) {
                        logger.debug("ParamName: {}", paramName);
                        logger.debug("ParamValue: {}", pValue.toString());
                        String myDate = pValue.toString();
                        java.util.Date parsedDate;

                        try {
                            SimpleDateFormat sdf = new SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH);
                            sdf.setLenient(false);
                            parsedDate = sdf.parse(myDate);
                        } catch (java.text.ParseException e) {
                            try {
                                SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
                                isoFormat.setLenient(false);
                                parsedDate = isoFormat.parse(myDate);
                            } catch (java.text.ParseException ex) {
                                throw new PlatformDataIntegrityException("error.msg.invalid.date.format",
                                        "Unable to parse date: " + myDate + ". Expected formats are 'dd MMMM yyyy' or 'yyyy-MM-dd'", ex);
                            }
                        }
                        java.sql.Date sqlDate = new java.sql.Date(parsedDate.getTime());
                        rptParamValues.put(paramName, sqlDate);
                    } else {
                        logger.debug("ParamName Unknown: {}", paramName);
                        logger.debug("ParamValue Unknown: {}", pValue.toString());
                        rptParamValues.put(paramName, pValue);
                    }
                }
            }

            // Tenant database name and current user's office hierarchy
            // passed as parameters to allow multitenant Pentaho reporting
            // and data scoping
            final var tenant = ThreadLocalContextUtil.getTenant();
            final var tenantConnection = tenant.getConnection();
            String protocol = toProtocol(this.tenantDataSource);
            Environment environment = contextVar.getEnvironment();
            String tenantUrl = toJdbcUrl(protocol, tenantConnection.getSchemaServer(), tenantConnection.getSchemaServerPort(),
                    tenantConnection.getSchemaName(), tenantConnection.getSchemaConnectionParameters());

            final var userhierarchy = currentUser.getOffice().getHierarchy();
            logger.debug("userHierarchy: {} ", userhierarchy);
            logger.debug("db URL: {} userhierarchy {}", tenantUrl, userhierarchy);

            rptParamValues.put("userhierarchy", userhierarchy);

            final var userid = currentUser.getId();
            var outPutInfo5 = "db URL:" + tenantUrl + "      userid:" + userid;
            logger.debug(outPutInfo5);

            rptParamValues.put("userid", userid);

            rptParamValues.put("tenantUrl", tenantUrl.trim());

            if (tenantConnection.getSchemaUsername().equalsIgnoreCase("") || tenantConnection.getSchemaUsername() == null) {
                rptParamValues.put("username", environment.getProperty("FINERACT_DEFAULT_TENANTDB_UID"));
            } else {
                rptParamValues.put("username", tenantConnection.getSchemaUsername().trim());
            }

            if (tenantConnection.getSchemaPassword().equalsIgnoreCase("") || tenantConnection.getSchemaPassword() == null) {
                rptParamValues.put("password", environment.getProperty("FINERACT_DEFAULT_TENANTDB_PWD"));
            } else {
                rptParamValues.put("password", databasePasswordEncryptor.decrypt(tenantConnection.getSchemaPassword()).trim());
            }

        } catch (Throwable t) {
            logger.error("error.msg.reporting.error:", t);
            throw new PlatformDataIntegrityException("error.msg.reporting.error", "Error adding parameters to report: " + t.getMessage());
        }
    }

    @Override
    public Map<String, String> getReportParams(final MultivaluedMap<String, String> queryParams) {
        final Map<String, String> reportParams = new HashMap<>();
        for (final Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            final String k = entry.getKey();
            if (k.startsWith("R_")) {
                final String pKey = k.substring(2);
                final String pValue = entry.getValue().get(0);
                reportParams.put(pKey, pValue);
            }
        }
        return reportParams;
    }

    private String getReportPath() {
        String reportPath = this.mifosBaseDir + File.separator + "pentahoReportsPostgres";
        File reportDir = new File(reportPath);
        if (!reportDir.exists()) {
            throw new PlatformDataIntegrityException("error.msg.reporting.directory.not.exists",
                    "Report directory does not exist: " + reportPath);
        }

        if (!reportDir.isDirectory()) {
            throw new PlatformDataIntegrityException("error.msg.reporting.path.not.directory",
                    "Report path is not a directory: " + reportPath);
        }

        if (!reportDir.canRead()) {
            throw new PlatformDataIntegrityException("error.msg.reporting.directory.not.readable",
                    "No read permission for report directory: " + reportPath);
        }

        if (reportDir.listFiles() == null || reportDir.listFiles().length <= 0) {
            throw new PlatformDataIntegrityException("error.msg.reporting.no.reports", "No reports found in directory: " + reportPath);
        }

        return reportPath + File.separator;
    }

    /**
     * Updates all data factories in the report and its subreports with the correct database connection details.
     *
     * @param report
     *            the master report to update
     * @throws SQLException
     *             if there's an error getting the database driver
     */
    private void updateAllDataFactories(MasterReport report) throws SQLException {
        // Update data factories in the master report
        DataFactory dataFactory = report.getDataFactory();
        if (dataFactory instanceof CompoundDataFactory) {
            CompoundDataFactory compoundDataFactory = (CompoundDataFactory) dataFactory;
            for (int i = 0; i < compoundDataFactory.size(); i++) {
                setConnectionDetail(compoundDataFactory.get(i));
            }
        } else {
            setConnectionDetail(dataFactory);
        }

        // Recursively update data factories in all subreports
        updateSubReportDataFactories(report);
    }

    /**
     * Recursively searches for and updates data factories in all subreports within a section.
     *
     * @param section
     *            the section to search for subreports
     * @throws SQLException
     *             if there's an error setting connection details
     */
    private void updateSubReportDataFactories(Section section) throws SQLException {
        if (section == null) {
            return;
        }

        int count = section.getElementCount();
        for (int i = 0; i < count; i++) {
            ReportElement element = section.getElement(i);

            if (element instanceof SubReport) {
                SubReport subReport = (SubReport) element;
                DataFactory subDataFactory = subReport.getDataFactory();

                if (subDataFactory instanceof CompoundDataFactory) {
                    CompoundDataFactory compoundDataFactory = (CompoundDataFactory) subDataFactory;
                    for (int j = 0; j < compoundDataFactory.size(); j++) {
                        setConnectionDetail(compoundDataFactory.get(j));
                    }
                } else if (subDataFactory != null) {
                    setConnectionDetail(subDataFactory);
                }

                // Recursively check for nested subreports
                updateSubReportDataFactories(subReport);
            } else if (element instanceof Section) {
                // Recursively search nested sections
                updateSubReportDataFactories((Section) element);
            }
        }
    }

    private void setConnectionDetail(DataFactory dataFactory) throws SQLException {
        final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        final FineractPlatformTenantConnection tenantConnection = tenant.getConnection();

        if (dataFactory instanceof SQLReportDataFactory) {
            SQLReportDataFactory sqlReportDataFactory = (SQLReportDataFactory) dataFactory;
            final DriverConnectionProvider connectionProvider = (DriverConnectionProvider) sqlReportDataFactory.getConnectionProvider();

            Driver e = DriverManager.getDriver(getTenantUrl());
            // Printing the driver
            logger.debug("Driver: {} ", e.getClass().getName().toString());
            connectionProvider.setDriver(e.getClass().getName().toString());
            connectionProvider.setUrl(getTenantUrl());
            connectionProvider.setProperty("user", tenantConnection.getSchemaUsername());
            logger.debug("Schema Username: {}", tenantConnection.getSchemaUsername());
            connectionProvider.setProperty("password", databasePasswordEncryptor.decrypt(tenantConnection.getSchemaPassword()).trim());
            sqlReportDataFactory.setConnectionProvider(connectionProvider);
        }
    }

    private String getTenantUrl() {
        final FineractPlatformTenant tenant = ThreadLocalContextUtil.getTenant();
        final FineractPlatformTenantConnection tenantConnection = tenant.getConnection();
        String protocol = toProtocol(tenantDataSource);
        // Default properties for Writing
        String schemaServer = tenantConnection.getSchemaServer();
        String schemaPort = tenantConnection.getSchemaServerPort();
        String schemaName = tenantConnection.getSchemaName();

        String schemaConnectionParameters = tenantConnection.getSchemaConnectionParameters();
        // Properties to ReadOnly case
        if (fineractProperties.getMode().isReadOnlyMode()) {
            schemaServer = getPropertyValue(tenantConnection.getReadOnlySchemaServer(), TenantConstants.PROPERTY_RO_SCHEMA_SERVER_NAME,
                    schemaServer);
            schemaPort = getPropertyValue(tenantConnection.getReadOnlySchemaServerPort(), TenantConstants.PROPERTY_RO_SCHEMA_SERVER_PORT,
                    schemaPort);
            schemaName = getPropertyValue(tenantConnection.getReadOnlySchemaName(), TenantConstants.PROPERTY_RO_SCHEMA_SCHEMA_NAME,
                    schemaName);

            schemaConnectionParameters = getPropertyValue(tenantConnection.getReadOnlySchemaConnectionParameters(),
                    TenantConstants.PROPERTY_RO_SCHEMA_CONNECTION_PARAMETERS, schemaConnectionParameters);
        }
        String jdbcUrl = toJdbcUrl(protocol, schemaServer, schemaPort, schemaName, schemaConnectionParameters);
        logger.debug("JDBC Url: {}", jdbcUrl);

        return jdbcUrl;
    }

    private String getPropertyValue(final String baseValue, final String propertyName, final String defaultValue) {
        // If the property already has set, return It
        if (null != baseValue) {
            return baseValue;
        }
        if (applicationContext == null) {
            return defaultValue;
        }
        return applicationContext.getEnvironment().getProperty(propertyName, defaultValue);
    }

    @Override
    public List<ReportExportType> getAvailableExportTargets() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
