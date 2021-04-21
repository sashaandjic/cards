//
//  Licensed to the Apache Software Foundation (ASF) under one
//  or more contributor license agreements.  See the NOTICE file
//  distributed with this work for additional information
//  regarding copyright ownership.  The ASF licenses this file
//  to you under the Apache License, Version 2.0 (the
//  "License"); you may not use this file except in compliance
//  with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing,
//  software distributed under the License is distributed on an
//  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  KIND, either express or implied.  See the License for the
//  specific language governing permissions and limitations
//  under the License.
//

import React, { useEffect, useState } from "react";

import {
  Grid,
  IconButton,
  Typography,
  Tooltip,
  withStyles
} from "@material-ui/core";

import { useTheme } from '@material-ui/core/styles';
import MaterialTable from "material-table";
import VocabularyActions from "./vocabularyActions"
import Search from "./search";

const Phase = require("./phaseCodes.json");

export default function VocabularyTable(props) {
  const { vocabList, type  } = props;
  const [filterTable, setFilterTable] = useState(false);
  const [acronymList, setAcronymList] = useState([]);
  const [rowCount, setRowCount] = useState(10);
  const [filteredVocabs, setFilteredVocabs] = useState([]);
  const [loading, setLoading] = React.useState(false);
  const theme = useTheme();

  useEffect(() => {
    if (filterTable) {
      if (acronymList.length == 0) {
        setFilteredVocabs([]);
      } else {
        setFilteredVocabs(vocabList.slice().filter(vocab => acronymList.includes(vocab.acronym)));
      }
    }
  }, [filterTable, acronymList])

  return(
    <React.Fragment>
      {(type === "remote") &&
      <Search
        // Provide Search with a function that allows it to concatenate a list to acronymList
        concatParentAcronymList={list => {
          setAcronymList(oldAcronymList =>
            // Create a Set with the concatenated list which removes duplicates
            // Use this duplicate free Set to make a list object and return it
            [...new Set(
              // New List = oldAcronymList + list
              oldAcronymList.concat(list)
              )
            ]);
        }}
        setParentAcronymList={setAcronymList}
        setParentFilterTable={setFilterTable}
        setLoading={setLoading}
        vocabList={vocabList}
      />
      }

      {(vocabList.length > 0) &&
      <Grid item>
        <MaterialTable
            columns={[
              { title: 'Id',
                cellStyle: {
                  width: '10%',
                  whiteSpace: "pre",
                },
                field: 'acronym'
              },
              { title: 'Name',
                cellStyle: {
                  width: "33%",
                },
                field: 'name'
              },
              { title: 'Version',
                cellStyle: {
                  width: "20%",
                  maxWidth: "100px",
                },
                filtering: false,
                sorting: false,
                render: rowData => rowData.version && <Tooltip title={rowData.version}><Typography style={{fontWeight: "inherit"}} noWrap>{rowData.version}</Typography></Tooltip>
              },
              {
                title: type === "local" ? "Installation Date" : "Release Date",
                cellStyle: {
                  width: "14%",
                  whiteSpace: "nowrap",
                },
                filtering: false,
                sorting: false,
                render: rowData => (new Date(type === "local" ? rowData.installed : rowData.released)).toString().substring(4,15)
              },
              { title: "",
                cellStyle: {
                  width: "23%",
                  whiteSpace: "pre",
                  textAlign: "right"
                },
                filtering: false,
                sorting: false,
                render: rowData => <VocabularyActions
                                     type={type}
                                     vocabulary={rowData}
                                     updateLocalList={props.updateLocalList}
                                     initPhase={props.acronymPhaseObject[rowData.acronym] || Phase["Not Installed"]}
                                     setPhase={(phase) => props.setPhase(rowData.acronym, phase)}
                                     addSetter={(setFunction) => props.addSetter(rowData.acronym, setFunction, type)}
                                   />
              }
            ]}
            data={filterTable ? filteredVocabs : vocabList}
            options={{
              toolbar: false,
              filtering: true,
              emptyRowsWhenPaging: false,
              addRowPosition: 'first',
              pageSize: rowCount,
              headerStyle: { backgroundColor: theme.palette.grey['200'],
                           },
            }}
            onChangeRowsPerPage={pageSize => {
              setRowCount(pageSize);
            }}
            isLoading={loading}
          />
      </Grid>
      }
    </React.Fragment>
  );
}
