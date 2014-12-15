/*
 * Copyright 2014 Hippo B.V. (http://www.onehippo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onehippo.repository.documentworkflow.action;

import org.apache.commons.scxml2.SCXMLExpressionException;
import org.apache.commons.scxml2.model.ModelException;
import org.onehippo.repository.documentworkflow.task.LogEventTask;

public class LogEventAction extends AbstractDocumentTaskAction<LogEventTask> {

    public void setAction(final String action) {
        setParameter("action", action);
    }

    public String getAction() {
        return getParameter("action");
    }

    @Override
    protected void initTask(final LogEventTask task) throws ModelException, SCXMLExpressionException {
        super.initTask(task);
        task.setAction(eval(getAction()));
    }

    @Override
    protected LogEventTask createWorkflowTask() {
        return new LogEventTask();
    }

}
