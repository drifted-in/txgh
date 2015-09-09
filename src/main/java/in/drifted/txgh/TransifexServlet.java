/* 
 * Copyright (c) 2014 Jan Tošovský <jan.tosovsky.cz@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package in.drifted.txgh;

import in.drifted.txgh.model.GitHubProject;
import in.drifted.txgh.util.TransifexProjectUtil;
import in.drifted.txgh.model.TransifexProject;
import in.drifted.txgh.model.TransifexResource;
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransifexServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransifexServlet.class);

    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        LOGGER.debug("Got some data... processing...");

        String reviewed = request.getParameter("reviewed");

        if (reviewed != null) {

            if (reviewed.equals("100")) {

                LOGGER.debug("Completed...");

                TransifexProject transifexProject = TransifexProjectUtil.getTransifexProject(request.getParameter("project"));
                TransifexResource transifexResource = transifexProject.getTransifexResource(request.getParameter("resource"));

                String language = request.getParameter("language");
                String sourceLanguage = transifexResource.getSourceLanguage();

                if (!language.equals(sourceLanguage)) {
                    String translation = new TransifexApi(transifexProject.getTransifexCredentials()).download(transifexResource, language);
                    String path = transifexResource.getTranslationPath(transifexProject.getLanguageData(language));
                    GitHubProject gitHubProject = transifexProject.getGitHubProject();
                    gitHubProject.getGitHubApi().commit(gitHubProject, path, translation);
                }

            } else {
                LOGGER.debug("Not yet completed... " + reviewed);
            }

        } else {
            LOGGER.debug("Ignoring unimportant request...");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

}
