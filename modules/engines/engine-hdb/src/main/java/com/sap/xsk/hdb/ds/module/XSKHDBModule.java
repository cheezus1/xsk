/*
 * Copyright (c) 2022 SAP SE or an SAP affiliate company and XSK contributors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License, v2.0
 * which accompanies this distribution, and is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * SPDX-FileCopyrightText: 2022 SAP SE or an SAP affiliate company and XSK contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.xsk.hdb.ds.module;

import java.util.HashMap;
import java.util.Map;


import com.sap.xsk.hdb.ds.parser.hdbdd.XSKHdbddParser;
import com.sap.xsk.hdb.ds.service.manager.IXSKEntityManagerService;
import com.sap.xsk.hdb.ds.parser.hdbscalarfunction.XSKHDBScalarFunctionParser;
import com.sap.xsk.hdb.ds.service.manager.IXSKScalarFunctionManagerService;
import com.sap.xsk.hdb.ds.parser.hdbtabletype.XSKTableTypeParser;
import com.sap.xsk.hdb.ds.service.manager.IXSKTableTypeManagerService;
import org.eclipse.dirigible.commons.api.module.AbstractDirigibleModule;

import com.sap.xsk.hdb.ds.api.IXSKDataStructureModel;
import com.sap.xsk.hdb.ds.parser.XSKDataStructureParser;
//import com.sap.xsk.hdb.ds.parser.hdbdd.XSKEntitiesParser;
import com.sap.xsk.hdb.ds.parser.hdbprocedure.XSKHDBProcedureParser;
import com.sap.xsk.hdb.ds.parser.hdbschema.XSKSchemaParser;
import com.sap.xsk.hdb.ds.parser.hdbsequence.XSKHDBSequenceParser;
import com.sap.xsk.hdb.ds.parser.hdbsynonym.XSKSynonymParser;
import com.sap.xsk.hdb.ds.parser.hdbtable.XSKTableParser;
import com.sap.xsk.hdb.ds.parser.hdbtablefunction.XSKHDBTableFunctionParser;
import com.sap.xsk.hdb.ds.parser.hdbview.XSKViewParser;
import com.sap.xsk.hdb.ds.service.manager.IXSKDataStructureManager;
import com.sap.xsk.hdb.ds.service.manager.IXSKHDBSequenceManagerService;
import com.sap.xsk.hdb.ds.service.manager.IXSKProceduresManagerService;
import com.sap.xsk.hdb.ds.service.manager.IXSKSchemaManagerService;
import com.sap.xsk.hdb.ds.service.manager.IXSKSynonymManagerService;
import com.sap.xsk.hdb.ds.service.manager.IXSKTableFunctionManagerService;
import com.sap.xsk.hdb.ds.service.manager.IXSKTableManagerService;
import com.sap.xsk.hdb.ds.service.manager.IXSKViewManagerService;

public class XSKHDBModule extends AbstractDirigibleModule {

  private static Map<String, IXSKDataStructureManager> managerServices;

  private static Map<String, XSKDataStructureParser> parserServices;

  private static Map<String, String> parserTypes;

  public static synchronized Map<String, IXSKDataStructureManager> getManagerServices() {
    if (managerServices == null) {
      managerServices = new HashMap<String, IXSKDataStructureManager>();
      bindManagerServicesToFileExtensions(managerServices);
    }
    return managerServices;
  }

  public static synchronized Map<String, XSKDataStructureParser> getParserServices() {
    if (parserServices == null) {
      parserServices = new HashMap<String, XSKDataStructureParser>();
      bindParsersToFileExtension(parserServices);
    }
    return parserServices;
  }

  public static synchronized Map<String, String> getParserTypes() {
    if (parserTypes == null) {
      parserTypes = new HashMap<String, String>();
      bindParserTypeToFileExtension(parserTypes);
    }
    return parserTypes;
  }

  @Override
  public void configure() {
  }

  private static void bindManagerServicesToFileExtensions(Map<String, IXSKDataStructureManager> managerServices) {
    managerServices.put(IXSKDataStructureModel.TYPE_HDBDD, new IXSKEntityManagerService());
    managerServices.put(IXSKDataStructureModel.TYPE_HDB_TABLE, new IXSKTableManagerService());
    managerServices.put(IXSKDataStructureModel.TYPE_HDB_VIEW, new IXSKViewManagerService());
    managerServices.put(IXSKDataStructureModel.TYPE_HDB_SYNONYM, new IXSKSynonymManagerService());
    managerServices.put(IXSKDataStructureModel.TYPE_HDB_TABLE_FUNCTION, new IXSKTableFunctionManagerService());
    managerServices.put(IXSKDataStructureModel.TYPE_HDB_SCHEMA, new IXSKSchemaManagerService());
    managerServices.put(IXSKDataStructureModel.TYPE_HDB_PROCEDURE, new IXSKProceduresManagerService());
    managerServices.put(IXSKDataStructureModel.TYPE_HDB_SEQUENCE, new IXSKHDBSequenceManagerService());
    managerServices.put(IXSKDataStructureModel.TYPE_HDB_SCALARFUNCTION, new IXSKScalarFunctionManagerService());
    managerServices.put(IXSKDataStructureModel.TYPE_HDB_TABLE_TYPE, new IXSKTableTypeManagerService());
  }

  private static void bindParsersToFileExtension(Map<String, XSKDataStructureParser> parserServices) {
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_ENTITIES, new XSKHdbddParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_TABLE, new XSKTableParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_VIEW, new XSKViewParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_SYNONYM, new XSKSynonymParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_HDBTABLEFUNCTION, new XSKHDBTableFunctionParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_HDBSCHEMA, new XSKSchemaParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_HDBPROCEDURE, new XSKHDBProcedureParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_HDBSEQUENCE, new XSKHDBSequenceParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_HDBSCALARFUNCTION, new XSKHDBScalarFunctionParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_STRUCTURE, new XSKTableTypeParser());
    parserServices.put(IXSKDataStructureModel.FILE_EXTENSION_HDB_TABLE_TYPE, new XSKTableTypeParser());

    parserServices.put(IXSKDataStructureModel.TYPE_HDBDD, new XSKHdbddParser());
    parserServices.put(IXSKDataStructureModel.TYPE_HDB_TABLE, new XSKTableParser());
    parserServices.put(IXSKDataStructureModel.TYPE_HDB_VIEW, new XSKViewParser());
    parserServices.put(IXSKDataStructureModel.TYPE_HDB_SYNONYM, new XSKSynonymParser());
    parserServices.put(IXSKDataStructureModel.TYPE_HDB_TABLE_FUNCTION, new XSKHDBTableFunctionParser());
    parserServices.put(IXSKDataStructureModel.TYPE_HDB_SCHEMA, new XSKSchemaParser());
    parserServices.put(IXSKDataStructureModel.TYPE_HDB_PROCEDURE, new XSKHDBProcedureParser());
    parserServices.put(IXSKDataStructureModel.TYPE_HDB_SEQUENCE, new XSKHDBSequenceParser());
    parserServices.put(IXSKDataStructureModel.TYPE_HDB_SCALARFUNCTION, new XSKHDBScalarFunctionParser());
    parserServices.put(IXSKDataStructureModel.TYPE_HDB_TABLE_TYPE, new XSKTableTypeParser());
  }

  private static void bindParserTypeToFileExtension(Map<String, String> parserTypes) {
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_ENTITIES, IXSKDataStructureModel.TYPE_HDBDD);
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_TABLE, IXSKDataStructureModel.TYPE_HDB_TABLE);
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_VIEW, IXSKDataStructureModel.TYPE_HDB_VIEW);
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_SYNONYM, IXSKDataStructureModel.TYPE_HDB_SYNONYM);
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_HDBTABLEFUNCTION, IXSKDataStructureModel.TYPE_HDB_TABLE_FUNCTION);
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_HDBSCHEMA, IXSKDataStructureModel.TYPE_HDB_SCHEMA);
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_HDBPROCEDURE, IXSKDataStructureModel.TYPE_HDB_PROCEDURE);
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_HDBSEQUENCE, IXSKDataStructureModel.TYPE_HDB_SEQUENCE);
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_HDBSCALARFUNCTION, IXSKDataStructureModel.TYPE_HDB_SCALARFUNCTION);
    parserTypes.put(IXSKDataStructureModel.FILE_EXTENSION_STRUCTURE, IXSKDataStructureModel.TYPE_HDB_TABLE_TYPE);
  }

  @Override
  public String getName() {
    return "XSK HDB Module";
  }
}
