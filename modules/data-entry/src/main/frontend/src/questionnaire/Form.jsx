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
import PropTypes from "prop-types";

import {
  Button,
  CircularProgress,
  Grid,
  Link,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  IconButton,
  withStyles
} from "@material-ui/core";
import CloseIcon from "@material-ui/icons/Close";

import QuestionnaireStyle, { FORM_ENTRY_CONTAINER_PROPS } from "./QuestionnaireStyle";
import FormEntry, { ENTRY_TYPES } from "./FormEntry";
import moment from "moment";
import { getHierarchy } from "./Subject";
import { SelectorDialog, parseToArray } from "./SubjectSelector";
import { FormProvider } from "./FormContext";
import DialogueLoginContainer from "../login/loginDialogue.js";
import DeleteButton from "../dataHomepage/DeleteButton";

// TODO Once components from the login module can be imported, open the login Dialog in-page instead of opening a popup window

// TODO Try to move the save-failed code somewhere more generic instead of the Form component

/**
 * Component that displays an editable Form.
 *
 * @example
 * <Form id="9399ca39-ab9a-4db4-bf95-7760045945fe"/>
 *
 * @param {string} id the identifier of a form; this is the JCR node name
 */
function Form (props) {
  let { classes, id } = props;
  // This holds the full form JSON, once it is received from the server
  let [ data, setData ] = useState();
  // Error message set when fetching the data from the server fails
  let [ error, setError ] = useState();
  // Marks that a save operation is in progress
  let [ saveInProgress, setSaveInProgress ] = useState();
  // Indicates whether the form has been saved or not. This has three possible values:
  // - undefined -> no save performed yet, or the form has been modified since the last save
  // - true -> data has been successfully saved
  // - false -> the save attempt failed
  // FIXME Replace this with a proper formState {unmodified, modified, saving, saved, saveFailed}
  let [ lastSaveStatus, setLastSaveStatus ] = useState(undefined);
  let [ selectorDialogOpen, setSelectorDialogOpen ] = useState(false);
  let [ selectorDialogError, setSelectorDialogError ] = useState("");
  let [ changedSubject, setChangedSubject ] = useState();
  let [ loginDialogShow, setLoginDialogShow ] = useState(false);
  let [ errorCode, setErrorCode ] = useState();
  let [ errorMessage, setErrorMessage ] = useState("");
  let [ errorDialogDisplayed, setErrorDialogDisplayed ] = useState(false);

  let formNode = React.useRef();

  // Fetch the form's data as JSON from the server.
  // The data will contain the form metadata,
  // such as authorship and versioning information, the associated subject,
  // the questionnaire definition,
  // and all the existing answers.
  // Once the data arrives from the server, it will be stored in the `data` state variable.
  let fetchData = () => {
    fetch(`/Forms/${id}.deep.json`)
      .then((response) => response.ok ? response.json() : Promise.reject(response))
      .then(handleResponse)
      .catch(handleFetchError);
  };

  // Callback method for the `fetchData` method, invoked when the data successfully arrived from the server.
  let handleResponse = (json) => {
    setData(json);
  };

  // Callback method for the `fetchData` method, invoked when the request failed.
  let handleFetchError = (response) => {
    setError(response);
    setData([]);  // Prevent an infinite loop if data was not set
  };

  // Event handler for the form submission event, replacing the normal browser form submission with a background fetch request.
  let saveData = (event) => {
    // This stops the normal browser form submission
    event && event.preventDefault();

    // If the previous save attempt failed, instead of trying to save again, open a login popup
    if (lastSaveStatus === false) {
      loginToSave();
      return;
    }

    setSaveInProgress(true);
    // currentTarget is the element on which the event listener was placed and invoked, thus the <form> element
    let data = new FormData(event ? event.currentTarget : formNode.current);
    fetch(`/Forms/${id}`, {
      method: "POST",
      body: data,
      headers: {
        Accept: "application/json"
      }
    }).then((response) => {
      if (response.ok) {
        setLastSaveStatus(true);
      } else if (response.status === 500) {
        response.json().then((json) => {
            setErrorCode(json["status.code"]);
            setErrorMessage(json.error.message);
            openErrorDialog();
        })
        setLastSaveStatus(undefined);
      } else {
        // If the user is not logged in, offer to log in
        const sessionInfo = window.Sling.getSessionInfo();
        if (sessionInfo === null || sessionInfo.userID === 'anonymous') {
          // On first attempt to save while logged out, set status to false to make button text inform user
          setLastSaveStatus(false);

        }
      }
      })
      .finally(() => setSaveInProgress(false));
  }

  // Open the login page in a new popup window, centered wrt the parent window
  let loginToSave = () => {
    setLastSaveStatus(undefined);
    setLoginDialogShow(true);
  }

  let handleLogin = (success) => {
    success && setLoginDialogShow(false);
    success && saveData();
  }

  // Handle when the subject of the form changes
  let changeSubject = (subject) => {
    setData( (old) => {
      let updated = {...old}
      updated.subject = subject;
      return(updated);
    })
    setChangedSubject(subject);
    setSelectorDialogOpen(false);
  }

  let openErrorDialog = () => {
    if (!errorDialogDisplayed) {
      setErrorDialogDisplayed(true);
    }
  }

  let closeErrorDialog = () => {
    if (errorDialogDisplayed) {
      setErrorDialogDisplayed(false);
    }
  }

  let handleSubmit = (event) => {
    // Do not save when login in progress
    // Prevents issue where submitting login dialog would try to save twice,
    // once before login complete and once after
    if (loginDialogShow === true) {
      return;
    }
    saveData(event);
  }

  // If the data has not yet been fetched, return an in-progress symbol
  if (!data) {
    fetchData();
    return (
      <Grid container justify="center"><Grid item><CircularProgress/></Grid></Grid>
    );
  }

  // If an error was returned, do not display a form at all, but report the error
  if (error) {
    return (
      <Grid container justify="center">
        <Grid item>
          <Typography variant="h2" color="error">
            Error obtaining form data: {error.status} {error.statusText}
          </Typography>
        </Grid>
      </Grid>
    );
  }

  let parentDetails = data?.subject?.parents && getHierarchy(data.subject.parents, React.Fragment, () => ({}));

  return (
    <form action={data["@path"]} method="POST" onSubmit={handleSubmit} onChange={()=>setLastSaveStatus(undefined)} key={id} ref={formNode}>
      <Grid container {...FORM_ENTRY_CONTAINER_PROPS} >
        <Grid item className={classes.formHeader} xs={12}>
          <Typography variant="overline">{parentDetails}</Typography>
          <Typography variant="h2">
            <Link href="#" onClick={() => {setSelectorDialogOpen(true)}}>
                {data?.subject?.identifier}
            </Link>
            {": " + (data?.questionnaire?.title || id || "")}
            <DeleteButton
              entryPath={data ? data["@path"] : "/Forms/"+id}
              entryName={(data?.subject?.identifier || "Subject") + ": " + (data?.questionnaire?.title || id || "")}
              entryType={data?.questionnaire?.title || "Form"}
              warning={data ? data["@referenced"] : false}
              shouldGoBack={true}
            />
          </Typography>
          {
            data && data['jcr:createdBy'] && data['jcr:created'] ?
            <Typography variant="overline">Entered by {data['jcr:createdBy']} on {moment(data['jcr:created']).format("dddd, MMMM Do YYYY")}</Typography>
            : ""
          }
        </Grid>
        <div className={classes.formProvider}></div>
        <FormProvider>
          <SelectorDialog
            allowedTypes={parseToArray(data?.['questionnaire']?.['requiredSubjectTypes'])}
            error={selectorDialogError}
            open={selectorDialogOpen}
            onChange={changeSubject}
            onClose={() => {setSelectorDialogOpen(false)}}
            onError={setSelectorDialogError}
            title="Set subject"
            />
          {changedSubject &&
            <React.Fragment>
              <input type="hidden" name={`${data["@path"]}/subject`} value={changedSubject["@path"]}></input>
              <input type="hidden" name={`${data["@path"]}/subject@TypeHint`} value="Reference"></input>
            </React.Fragment>
          }
          {
            Object.entries(data.questionnaire)
              .filter(([key, value]) => ENTRY_TYPES.includes(value['jcr:primaryType']))
              .map(([key, entryDefinition]) => <FormEntry key={key} entryDefinition={entryDefinition} path={"."} depth={0} existingAnswers={data} keyProp={key} classes={classes} onChange={()=>setLastSaveStatus(undefined)}></FormEntry>)
          }
        </FormProvider>
      </Grid>

      <Button
        type="submit"
        variant="contained"
        color="primary"
        disabled={saveInProgress}
        className={classes.saveButton}
      >
        {saveInProgress ? 'Saving' :
        lastSaveStatus === true ? 'Saved' :
        lastSaveStatus === false ? 'Save failed, log in and try again?' :
        'Save'}
      </Button>
      <DialogueLoginContainer isOpen={loginDialogShow} handleLogin={handleLogin}/>
      <Dialog open={errorDialogDisplayed} onClose={closeErrorDialog}>
        <DialogTitle disableTypography>
          <Typography variant="h6" color="error" className={classes.dialogTitle}>Failed to save</Typography>
          <IconButton onClick={closeErrorDialog} className={classes.closeButton}>
            <CloseIcon />
          </IconButton>
        </DialogTitle>
        <DialogContent>
            <Typography variant="body1">Server responded with response code {errorCode}:<br />{errorMessage}</Typography>
        </DialogContent>
      </Dialog>
    </form>
  );
};

export default withStyles(QuestionnaireStyle)(Form);
