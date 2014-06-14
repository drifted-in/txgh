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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import in.drifted.txgh.model.TransifexCredentials;
import in.drifted.txgh.model.TransifexResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransifexApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransifexApi.class);
    private static final String TARGET_URL = "https://www.transifex.com";
    private static final String API_ROOT = "/api/2";
    private final Client client;

    public TransifexApi(TransifexCredentials credentials) {

        HttpAuthenticationFeature feature = HttpAuthenticationFeature.basic(credentials.getUsername(), credentials.getPassword());
        client = ClientBuilder.newBuilder()
                .register(feature)
                .register(MultiPartFeature.class)
                .build();
    }

    public void update(TransifexResource resource, String content) throws IOException {

        Path fileToUpload = Files.createTempFile("fileToUpload", ".tmp");
        Files.write(fileToUpload, content.getBytes(StandardCharsets.UTF_8));

        FormDataMultiPart multiPart = new FormDataMultiPart();
        multiPart.bodyPart(new FileDataBodyPart("file", fileToUpload.toFile()));

        String method;

        StringBuilder url = new StringBuilder();
        url.append(API_ROOT);
        url.append("/project/");
        url.append(resource.getProjectSlug());

        if (resourceExists(resource)) {
            method = "PUT";
            url.append("/resource/");
            url.append(resource.getResourceSlug());
            url.append("/content/");
        } else {
            method = "POST";
            url.append("/resources/");
            multiPart.field("slug", resource.getResourceSlug());
            multiPart.field("name", resource.getSourceFile());
            multiPart.field("i18n_type", resource.getType());
        }

        WebTarget webTarget = client.target(TARGET_URL + url);
        Response response = webTarget.request().method(method, Entity.entity(multiPart, multiPart.getMediaType()));

        String jsonData = response.readEntity(String.class);

        if (response.getStatus() / 100 != 2) {
            LOGGER.debug(jsonData);
            throw new IOException("Transifex API call has failed. Status code: " + response.getStatus());
        }

        LOGGER.debug(jsonData);
    }

    public Boolean resourceExists(TransifexResource resource) {

        StringBuilder url = new StringBuilder();
        url.append(API_ROOT);
        url.append("/project/");
        url.append(resource.getProjectSlug());
        url.append("/resource/");
        url.append(resource.getResourceSlug());

        WebTarget webTarget = client.target(TARGET_URL + url);
        Response response = webTarget.request(MediaType.TEXT_PLAIN).get();

        return response.getStatus() == 200;
    }

    public String download(TransifexResource resource, String language) throws IOException {

        StringBuilder url = new StringBuilder();
        url.append(API_ROOT);
        url.append("/project/");
        url.append(resource.getProjectSlug());
        url.append("/resource/");
        url.append(resource.getResourceSlug());
        url.append("/translation/");
        url.append(language);

        WebTarget webTarget = client.target(TARGET_URL + url);
        Response response = webTarget.request(MediaType.TEXT_PLAIN).get();

        String jsonData = response.readEntity(String.class);

        if (response.getStatus() / 100 != 2) {
            LOGGER.debug(jsonData);
            throw new IOException("Transifex API call has failed. Status code: " + response.getStatus());
        }

        JsonObject jsonObject = new JsonParser().parse(jsonData).getAsJsonObject();

        return jsonObject.get("content").getAsString();
    }

}
