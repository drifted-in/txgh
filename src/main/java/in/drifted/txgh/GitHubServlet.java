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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import in.drifted.txgh.model.GitHubProject;
import in.drifted.txgh.model.TransifexProject;
import in.drifted.txgh.model.TransifexResource;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.Tree;
import org.eclipse.egit.github.core.TreeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHubServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubServlet.class);
    private static final String GITHUB_BASE_URL = "https://github.com/";

    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        LOGGER.debug("Got some data... processing...");

        String payload = request.getParameter("payload");

        if (payload != null) {

            Map<String, Object> parameterMap = request.getParameterMap();
            for (Map.Entry<String, Object> entry : parameterMap.entrySet()) {
                if (entry.getValue() instanceof String[]) {
                    LOGGER.debug(entry.getKey() + "::" + Arrays.toString((String[]) entry.getValue()));
                }
            }

            JsonObject payloadObject = new JsonParser().parse(payload).getAsJsonObject();

            if (payloadObject.get("ref").getAsString().equals("refs/heads/master")) {

                JsonObject repository = payloadObject.get("repository").getAsJsonObject();
                JsonObject owner = repository.get("owner").getAsJsonObject();
                String ownerName = owner.get("name").getAsString();
                String repositoryName = repository.get("name").getAsString();

                String gitHubUrl = GITHUB_BASE_URL + ownerName + "/" + repositoryName;
                GitHubProject gitHubProject = new GitHubProject(gitHubUrl);
                GitHubApi gitHubApi = gitHubProject.getGitHubApi();
                Repository gitHubRepository = gitHubApi.getRepository(gitHubUrl);
                TransifexProject transifexProject = gitHubProject.getTransifexProject();

                Map<String, TransifexResource> sourceFileMap = transifexProject.getSourceFileMap();
                Map<String, TransifexResource> updatedTransifexResourceMap = new LinkedHashMap<>();

                for (JsonElement commitElement : payloadObject.get("commits").getAsJsonArray()) {
                    JsonObject commitObject = commitElement.getAsJsonObject();
                    for (JsonElement modified : commitObject.get("modified").getAsJsonArray()) {
                        String modifiedSourceFile = modified.getAsString();
                        LOGGER.debug("Modified source file: " + modifiedSourceFile);
                        if (sourceFileMap.containsKey(modifiedSourceFile)) {
                            LOGGER.debug("Watched source file has been found: " + modifiedSourceFile);
                            updatedTransifexResourceMap.put(commitObject.get("id").getAsString(), sourceFileMap.get(modifiedSourceFile));
                        }
                    }
                }

                for (Entry<String, TransifexResource> entry : updatedTransifexResourceMap.entrySet()) {

                    String sourceFile = entry.getValue().getSourceFile();
                    LOGGER.debug("Modified source file (watched): " + sourceFile);
                    String treeSha = gitHubApi.getCommitTreeSha(gitHubRepository, entry.getKey());
                    Tree tree = gitHubApi.getTree(gitHubRepository, treeSha);
                    for (TreeEntry file : tree.getTree()) {
                        LOGGER.debug("Repository file: " + file.getPath());
                        if (sourceFile.equals(file.getPath())) {
                            transifexProject.getTransifexApi().update(entry.getValue(), gitHubApi.getFileContent(gitHubRepository, file.getSha()));
                            break;
                        }
                    }
                }
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
