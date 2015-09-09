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

import in.drifted.txgh.model.GitHubCredentials;
import in.drifted.txgh.model.GitHubProject;
import in.drifted.txgh.model.GitHubProjectConfig;
import in.drifted.txgh.model.GitHubUser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.eclipse.egit.github.core.Blob;
import org.eclipse.egit.github.core.Commit;
import org.eclipse.egit.github.core.CommitUser;
import org.eclipse.egit.github.core.Reference;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.Tree;
import org.eclipse.egit.github.core.TreeEntry;
import org.eclipse.egit.github.core.TypedResource;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.DataService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHubApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubApi.class);
    private final GitHubClient gitHubClient;
    private final RepositoryService repositoryService;
    private final DataService dataService;
    private final CommitUser gitHubCommitUser;

    public GitHubApi(GitHubProjectConfig config) {

        GitHubCredentials gitHubCredentials = config.getGitHubCredentials();
        GitHubUser gitHubUser = config.getGitHubUser();

        gitHubClient = new GitHubClient();
        gitHubClient.setCredentials(gitHubCredentials.getUsername(), gitHubCredentials.getPassword());
        this.gitHubCommitUser = getGitHubCommitUser(gitHubUser.getName(), gitHubUser.getEmail());
        repositoryService = new RepositoryService(gitHubClient);
        dataService = new DataService(gitHubClient);
    }

    private CommitUser getGitHubCommitUser(String name, String email) {

        CommitUser commitUser = new CommitUser();

        commitUser.setName(name);
        commitUser.setEmail(email);
        commitUser.setDate(Calendar.getInstance().getTime());

        return commitUser;
    }

    public Repository getRepository(String gitHubUrl) throws IOException {
        return repositoryService.getRepository(RepositoryId.createFromUrl(gitHubUrl));
    }

    public Commit getCommit(Repository repository, String sha) throws IOException {
        return dataService.getCommit(repository, sha);
    }

    public String getCommitTreeSha(Repository repository, String sha) throws IOException {
        return getCommit(repository, sha).getTree().getSha();
    }

    public Tree getTree(Repository repository, String sha) throws IOException {
        return dataService.getTree(repository, sha, true);
    }

    public Blob getBlob(Repository repository, String sha) throws IOException {
        return dataService.getBlob(repository, sha);
    }

    public String getFileContent(Repository repository, String sha) throws IOException {
        Blob blob = getBlob(repository, sha);
        return blob.getEncoding().equalsIgnoreCase("utf-8") ? blob.getContent() : new String(Base64.getDecoder().decode(blob.getContent().replace("\n", "")), StandardCharsets.UTF_8);
    }

    public void commit(GitHubProject project, String path, String content) throws IOException {

        LOGGER.debug("Entering commit procedure for path: " + path);

        Repository repository = getRepository(project.getProjectUrl());

        Blob blob = new Blob();
        blob.setContent(content);

        String blobSha = dataService.createBlob(repository, blob);
        Reference master = dataService.getReference(repository, "heads/master");
        Commit baseCommit = dataService.getCommit(repository, master.getObject().getSha());

        TreeEntry treeEntry = new TreeEntry();
        treeEntry.setPath(path);
        treeEntry.setMode(TreeEntry.MODE_BLOB);
        treeEntry.setType(TreeEntry.TYPE_BLOB);
        treeEntry.setSha(blobSha);

        Collection<TreeEntry> treeEntryCollection = new HashSet<>();
        treeEntryCollection.add(treeEntry);

        Tree tree = dataService.createTree(repository, treeEntryCollection, baseCommit.getTree().getSha());

        Commit commit = new Commit();
        commit.setMessage("Updating translations for " + path);
        commit.setAuthor(gitHubCommitUser);
        commit.setCommitter(gitHubCommitUser);
        commit.setTree(tree);
        List<Commit> parentCommitList = new ArrayList<>();
        parentCommitList.add(baseCommit);
        commit.setParents(parentCommitList);

        Commit finalCommit = dataService.createCommit(repository, commit);

        TypedResource resource = new TypedResource();
        resource.setType(TypedResource.TYPE_COMMIT);
        resource.setUrl(finalCommit.getUrl());
        resource.setSha(finalCommit.getSha());

        Reference reference = new Reference();
        reference.setObject(resource);
        reference.setRef(master.getRef());

        dataService.editReference(repository, reference);
    }
}
