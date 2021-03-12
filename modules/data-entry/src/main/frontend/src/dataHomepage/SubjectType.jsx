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
import React, { useState } from "react";

import QuestionnaireStyle from "../questionnaire/QuestionnaireStyle.jsx";

import { Button, Card, CardContent, CardHeader, Typography, withStyles } from "@material-ui/core";
import SubjectDirectory from "../questionnaire/SubjectDirectory.jsx";

/**
 * Component that displays the subjects related to a SubjectType (specified in url).
 *
 */
function SubjectType(props) {
  const { classes } = props;
  let [subjectID, setSubjectID] = useState();
  let [ error, setError ] = useState();
  let [initialized, setInitialized] = useState(false);

  // inspect the URL to get the 'label' of the current SubjectType and the title to be used
  const typeLabel = /content.html\/(.+)/.exec(location.pathname)[1];
  const currentTitle = `${typeLabel}s`;

  let initialize = () => {
    setInitialized(true);

    // Fetch the jcr:uuid of the current SubjectType, based on the 'label'
    let url = (`/query?query=SELECT * FROM [lfs:SubjectType] as n WHERE n.label='${typeLabel}'`);
    fetch(url)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then((response) => {
        // jcr:uuid of current SubjectType will be stored in `subjectID`
        setSubjectID(response["rows"][0]["jcr:uuid"]);
      })
      .catch(handleError);
  }

  // Callback method for the `initialize` method, invoked when the request failed.
  let handleError = (response) => {
    setError(response.statusText ? response.statusText : response.toString());
    setSubjectID([]);
  };

  // If an error was returned, report the error
  if (error) {
    return (
      <Card>
        <CardHeader title="Error"/>
        <CardContent>
          <Typography>{error}</Typography>
        </CardContent>
      </Card>
    );
  }

  // If no forms can be obtained, we do not want to keep on re-obtaining questionnaires
  if (!initialized) {
    initialize();
  }

  // redirect to subject page on click
  const entry = /Subjects\/(.+)/.exec(location.pathname);
  if (entry) {
    return <Subject id={entry[1]} contentOffset={props.contentOffset} />;
  }

  return (
    <React.Fragment>
      { subjectID &&
        <Card>
          <CardHeader
            title={<Typography variant="h6">{currentTitle}</Typography>}
          />
          <CardContent>
            <SubjectDirectory id={subjectID} disableTopPagination={true} />
          </CardContent>
        </Card>
      }
    </React.Fragment>
  );
}

export default withStyles(QuestionnaireStyle)(SubjectType);
