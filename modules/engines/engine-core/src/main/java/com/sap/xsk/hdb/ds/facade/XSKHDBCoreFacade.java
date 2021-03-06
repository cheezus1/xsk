/*
 * Copyright (c) 2019-2021 SAP SE or an SAP affiliate company and XSK contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-FileCopyrightText: 2019-2021 SAP SE or an SAP affiliate company and XSK contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.xsk.hdb.ds.facade;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.sap.xsk.hdb.ds.api.IXSKDataStructureModel;
import com.sap.xsk.hdb.ds.api.IXSKEnvironmentVariables;
import com.sap.xsk.hdb.ds.api.XSKDataStructuresException;
import com.sap.xsk.hdb.ds.model.XSKDataStructureModel;
import com.sap.xsk.hdb.ds.model.hdbdd.XSKDataStructureEntitiesModel;
import com.sap.xsk.hdb.ds.model.hdbdd.XSKDataStructureEntityModel;
import com.sap.xsk.hdb.ds.model.hdbprocedure.XSKDataStructureHDBProcedureModel;
import com.sap.xsk.hdb.ds.model.hdbschema.XSKDataStructureHDBSchemaModel;
import com.sap.xsk.hdb.ds.model.hdbtable.XSKDataStructureHDBTableModel;
import com.sap.xsk.hdb.ds.model.hdbtablefunction.XSKDataStructureHDBTableFunctionModel;
import com.sap.xsk.hdb.ds.model.hdbview.XSKDataStructureHDBViewModel;
import com.sap.xsk.hdb.ds.service.manager.IXSKDataStructureManager;
import com.sap.xsk.hdb.ds.service.parser.IXSKCoreParserService;
import com.sap.xsk.utils.XSKUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.dirigible.commons.config.Configuration;
import org.eclipse.dirigible.core.scheduler.api.SynchronizationException;
import org.eclipse.dirigible.database.ds.model.IDataStructureModel;
import org.eclipse.dirigible.database.sql.SqlFactory;
import org.eclipse.dirigible.repository.api.IResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.text.MessageFormat.format;

public class XSKHDBCoreFacade implements IXSKHDBCoreFacade {

    private static final Logger logger = LoggerFactory.getLogger(XSKHDBCoreFacade.class);

    @javax.inject.Inject
    private DataSource dataSource;

    @Inject
    private Map<String, IXSKDataStructureManager> managerServices;

    @Inject
    @Named("xskCoreParserService")
    private IXSKCoreParserService xskCoreParserService;

    @Override
    public void handleResourceSynchronization(IResource resource) throws SynchronizationException, XSKDataStructuresException {
        String[] splitResourceName = resource.getName().split("\\.");
        String resourceExtension = "." + splitResourceName[splitResourceName.length - 1];
        String registryPath = getRegistryPath(resource);

        String contentAsString = getContent(resource);
        XSKDataStructureModel dataStructureModel;
        try {
            dataStructureModel = xskCoreParserService.parseDataStructure(resourceExtension, registryPath, contentAsString);
        } catch (XSKDataStructuresException e) {
            logger.error("Synchronized artifact is not valid");
            logger.error(e.getMessage());
            return;
        } catch (Exception e) {
            throw new SynchronizationException(e);
        }

        dataStructureModel.setLocation(registryPath);
        managerServices.get(dataStructureModel.getType()).synchronizeRuntimeMetadata(dataStructureModel);
    }

    @Override
    public void handleResourceSynchronization(String fileExtension, XSKDataStructureModel dataStructureModel) throws SynchronizationException, XSKDataStructuresException {
        managerServices.get(dataStructureModel.getType()).synchronizeRuntimeMetadata(dataStructureModel);
    }

    @Override
    public void updateEntities() {
        Map<String, XSKDataStructureModel> dataStructureEntitiesModel = managerServices.get(IXSKDataStructureModel.TYPE_HDB_ENTITIES).getDataStructureModels();
        Map<String, XSKDataStructureModel> dataStructureTablesModel = managerServices.get(IXSKDataStructureModel.TYPE_HDB_TABLE).getDataStructureModels();
        Map<String, XSKDataStructureModel> dataStructureViewsModel = managerServices.get(IXSKDataStructureModel.TYPE_HDB_VIEW).getDataStructureModels();
        Map<String, XSKDataStructureModel> dataStructureProceduresModel = managerServices.get(IXSKDataStructureModel.TYPE_HDB_PROCEDURE).getDataStructureModels();
        Map<String, XSKDataStructureModel> dataStructureTableFunctionsModel = managerServices.get(IXSKDataStructureModel.TYPE_HDB_TABLE_FUNCTION).getDataStructureModels();
        Map<String, XSKDataStructureModel> dataStructureSchemasModel = managerServices.get(IXSKDataStructureModel.TYPE_HDB_SCHEMA).getDataStructureModels();

        if (dataStructureEntitiesModel.isEmpty()
                && dataStructureTablesModel.isEmpty()
                && dataStructureViewsModel.isEmpty()
                && dataStructureProceduresModel.isEmpty()
                && dataStructureTableFunctionsModel.isEmpty()
                && dataStructureSchemasModel.isEmpty()) {
            logger.trace("No XSK Data Structures to update.");
            return;
        }

        List<String> errors = new ArrayList<>();
        try {
            Connection connection = null;
            try {
                connection = dataSource.getConnection();

                List<String> sorted = new ArrayList<>();

                // something wrong happened with the sorting - probably cyclic dependencies
                // we go for the back-up list and try to apply what would succeed
                // Probably there are cyclic dependencies!
                sorted.addAll(dataStructureEntitiesModel.keySet());
                sorted.addAll(dataStructureTablesModel.keySet());
                sorted.addAll(dataStructureViewsModel.keySet());
                sorted.addAll(dataStructureProceduresModel.keySet());
                sorted.addAll(dataStructureTableFunctionsModel.keySet());
                sorted.addAll(dataStructureSchemasModel.keySet());

                boolean hdiOnly = Boolean.parseBoolean(Configuration.get(IXSKEnvironmentVariables.XSK_HDI_ONLY, "false"));
                if (!hdiOnly) {

                    // drop HDB Procedures
                    List<XSKDataStructureHDBProcedureModel> hdbProceduresToUpdate = new ArrayList<>();
                    for (int i = sorted.size() - 1; i >= 0; i--) {
                        String dsName = sorted.get(i);
                        XSKDataStructureHDBProcedureModel model = (XSKDataStructureHDBProcedureModel) dataStructureProceduresModel.get(dsName);
                        if (model != null) {
                            hdbProceduresToUpdate.add(model);
                        }
                    }

                    IXSKDataStructureManager<XSKDataStructureModel> xskProceduresManagerService = managerServices.get(IXSKDataStructureModel.TYPE_HDB_PROCEDURE);
                    for (XSKDataStructureHDBProcedureModel procedureToUpdate :
                            hdbProceduresToUpdate) {
                        xskProceduresManagerService.dropDataStructure(connection, procedureToUpdate);
                    }

                    // drop HDB Table Functions
                    List<XSKDataStructureHDBTableFunctionModel> hdbTableFunctionsToUpdate = new ArrayList<>();
                    for (int i = sorted.size() - 1; i >= 0; i--) {
                        String dsName = sorted.get(i);
                        XSKDataStructureHDBTableFunctionModel model = (XSKDataStructureHDBTableFunctionModel) dataStructureTableFunctionsModel.get(dsName);
                        if (model != null) {
                            hdbTableFunctionsToUpdate.add(model);
                        }
                    }

                    IXSKDataStructureManager<XSKDataStructureModel> xskTableFunctionManagerService = managerServices.get(IXSKDataStructureModel.TYPE_HDB_TABLE_FUNCTION);
                    for (XSKDataStructureHDBTableFunctionModel tableFunctionModel :
                            hdbTableFunctionsToUpdate) {
                        xskTableFunctionManagerService.dropDataStructure(connection, tableFunctionModel);
                    }

                    // drop views in a reverse order
                    IXSKDataStructureManager<XSKDataStructureModel> xskViewManagerService = managerServices.get(IXSKDataStructureModel.TYPE_HDB_VIEW);

                    for (int i = sorted.size() - 1; i >= 0; i--) {
                        String dsName = sorted.get(i);
                        XSKDataStructureHDBViewModel model = (XSKDataStructureHDBViewModel) dataStructureViewsModel.get(dsName);
                        try {
                            if (model != null) {
                                xskViewManagerService.dropDataStructure(connection, model);
                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            errors.add(e.getMessage());
                        }
                    }

                    IXSKDataStructureManager<XSKDataStructureModel> xskTableManagerService = managerServices.get(IXSKDataStructureModel.TYPE_HDB_TABLE);

                    // drop tables in a reverse order
                    for (int i = sorted.size() - 1; i >= 0; i--) {
                        String dsName = sorted.get(i);
                        XSKDataStructureHDBTableModel model = (XSKDataStructureHDBTableModel) dataStructureTablesModel.get(dsName);
                        try {
                            if (model != null && SqlFactory.getNative(connection).exists(connection, model.getName())) {
                                if (SqlFactory.getNative(connection).count(connection, model.getName()) == 0) {
                                    xskTableManagerService.dropDataStructure(connection, model);
                                } else {
                                    logger.warn(format("Table [{0}] cannot be deleted during the update process, because it is not empty", dsName));
                                }

                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            errors.add(e.getMessage());
                        }
                    }

                    IXSKDataStructureManager<XSKDataStructureModel> xskEntityManagerService = managerServices.get(IXSKDataStructureModel.TYPE_HDB_ENTITIES);
                    boolean caseSensitive = Boolean.parseBoolean(Configuration.get(IDataStructureModel.DIRIGIBLE_DATABASE_NAMES_CASE_SENSITIVE, "false"));

                    // drop entities in a reverse order
                    for (int i = sorted.size() - 1; i >= 0; i--) {
                        String dsName = sorted.get(i);
                        XSKDataStructureEntitiesModel entitiesModel = (XSKDataStructureEntitiesModel) dataStructureEntitiesModel.get(dsName);
                        try {
                            if (entitiesModel != null) {
                                for (XSKDataStructureEntityModel entityModel : entitiesModel.getContext().getЕntities()) {
                                    String tableName = XSKUtils.getTableName(entityModel);
                                    if (caseSensitive) {
                                        tableName = "\"" + tableName + "\"";
                                    }
                                    if (SqlFactory.getNative(connection).exists(connection, tableName)) {
                                        if (SqlFactory.getNative(connection).count(connection, tableName) == 0) {
                                            xskEntityManagerService.dropDataStructure(connection, entityModel);
                                        } else {
                                            logger.warn(format("Entity [{0}] cannot be deleted during the update process, because it is not empty", dsName));
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            errors.add(e.getMessage());
                        }
                    }

                    // drop HDB Schemas
                    List<XSKDataStructureHDBSchemaModel> hdbSchemasToUpdate = new ArrayList<>();
                    for (int i = sorted.size() - 1; i >= 0; i--) {
                        String dsName = sorted.get(i);
                        XSKDataStructureHDBSchemaModel model = (XSKDataStructureHDBSchemaModel) dataStructureSchemasModel.get(dsName);
                        if (model != null) {
                            hdbSchemasToUpdate.add(model);
                        }
                    }

                    IXSKDataStructureManager<XSKDataStructureModel> xskSchemaManagerService = managerServices.get(IXSKDataStructureModel.TYPE_HDB_SCHEMA);
                    // executeHDBSchemasDrop(connection, hdbSchemasToUpdate);
                    for (XSKDataStructureHDBSchemaModel schemaModel :
                            hdbSchemasToUpdate) {
                        xskSchemaManagerService.dropDataStructure(connection, schemaModel);
                    }

                    // process tables in the proper order
                    for (String dsName : sorted) {
                        XSKDataStructureHDBTableModel model = (XSKDataStructureHDBTableModel) dataStructureTablesModel.get(dsName);
                        try {
                            if (model != null) {
                                if (!SqlFactory.getNative(connection).exists(connection, model.getName())) {
                                    xskTableManagerService.createDataStructure(connection, model);
                                } else {
                                    logger.warn(format("Table [{0}] already exists during the update process", dsName));
                                    if (SqlFactory.getNative(connection).count(connection, model.getName()) != 0) {
                                        xskTableManagerService.updateDataStructure(connection, model);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            errors.add(e.getMessage());
                        }
                    }

                    // process entities in the proper order
                    for (String dsName : sorted) {
                        XSKDataStructureEntitiesModel entitesModel = (XSKDataStructureEntitiesModel) dataStructureEntitiesModel.get(dsName);
                        try {
                            if (entitesModel != null) {
                                for (XSKDataStructureEntityModel entityModel : entitesModel.getContext().getЕntities()) {
                                    String tableName = XSKUtils.getTableName(entityModel);
                                    if (caseSensitive) {
                                        tableName = "\"" + tableName + "\"";
                                    }
                                    if (!SqlFactory.getNative(connection).exists(connection, tableName)) {
                                        xskEntityManagerService.createDataStructure(connection, entityModel);
                                    } else {
                                        xskEntityManagerService.updateDataStructure(connection, entityModel);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            errors.add(e.getMessage());
                        }
                    }

                    // process views in the proper order
                    for (String dsName : sorted) {
                        XSKDataStructureHDBViewModel model = (XSKDataStructureHDBViewModel) dataStructureViewsModel.get(dsName);
                        try {
                            if (model != null) {
                                if (!SqlFactory.getNative(connection).exists(connection, model.getName())) {
                                    xskViewManagerService.createDataStructure(connection, model);
                                } else {
                                    logger.warn(format("View [{0}] already exists during the update process", dsName));
                                }
                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                            errors.add(e.getMessage());
                        }
                    }


                    // process hdbProcedures
                    for (XSKDataStructureHDBProcedureModel procedureModel :
                            hdbProceduresToUpdate) {
                        xskProceduresManagerService.createDataStructure(connection, procedureModel);
                    }

                    // process hdbTableFunctions
                    for (XSKDataStructureHDBTableFunctionModel tableFunctionModel :
                            hdbTableFunctionsToUpdate) {
                        xskTableFunctionManagerService.createDataStructure(connection, tableFunctionModel);
                    }
                }
            } finally {
                if (connection != null) {
                    connection.close();
                }
            }
        } catch (SQLException e) {
            logger.error(concatenateListOfStrings(errors), e);
        }
    }

    @Override
    public void cleanup() throws XSKDataStructuresException {
        for (IXSKDataStructureManager dataStructureManager :
                managerServices.values()) {
            dataStructureManager.cleanup();
        }

        logger.trace("Done cleaning up XSK Data Structures.");
    }

    @Override
    public void clearCache() {
        this.managerServices.values().forEach(IXSKDataStructureManager::clearCache);
    }


    private String getRegistryPath(IResource resource) {
        String resourcePath = resource.getPath();
        return resourcePath.startsWith("/registry/public") ? resourcePath.substring("/registry/public".length()) : resourcePath;
    }

    private String getContent(IResource resource) throws SynchronizationException {
        byte[] content = resource.getContent();
        String contentAsString;
        try {
            contentAsString = IOUtils.toString(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SynchronizationException(e);
        }
        return contentAsString;
    }

    /**
     * Concatenate list of strings.
     *
     * @param list the list
     * @return the string
     */
    private static String concatenateListOfStrings(List<String> list) {
        StringBuilder buff = new StringBuilder();
        for (String s : list) {
            buff.append(s).append("\n---\n");
        }
        return buff.toString();
    }
}
