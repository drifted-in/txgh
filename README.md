txgh
====
Synchronization app for Transifex/GitHub projects.

How it works
============
Both Transifex and GitHub can send POST requests when specific events happen. This Java servlet can consume these requests and when specific ones are detected, synchronize the updated content.

  * The GitHub content is updated only when the translation to the particular language in Transifex is completed and all texts are verified.
  * The Transifex resource is updated only when the corresponding GitHub source file is modified. GitHub updates in translated files are out of sync.


Configuration
=============
For correct functionality it is necessary to set up both Transifex and GitHub hooks in your projects settings. The mapping between both projects is specified in a special JSON configuration file. 

This file is stored in `/src/main/resources/in/drifted/txgh/resources/config.json`. It specifies the project name, credentials, the commit user (both name and email address has to match the GitHub account), the project to synchronize and in case of Transifex projects also a special resources configuration file.

    {
        "gitHubProjectConfigMap" : {
            "https://github.com/drifted-in/project" : {
                "gitHubCredentials" : {"username" : "github-user", "password" : "github-password"},
                "gitHubUser" : {"name" : "github-user-name", "email" : "github-user-email"},
                "transifexProjectName" : "transifex-project"
            }
        },

        "transifexProjectConfigMap" : {
            "transifex-project" : {
                "transifexConfigPath" : "transifex-project.cfg",
                "transifexCredentials" : {"username" : "transifex-user", "password" : "transifex-password"},
                "gitHubProjectUrl" : "https://github.com/drifted-in/project"
            }
        }
    }

The [Transifex configuration file](http://docs.transifex.com/client/config/) specifies available resources for the given project, file naming conventions and the source file format. Exactly the same configuration file is used by Transifex client application. All these configuration files are supposed to be located in the `/src/main/resources/in/drifted/txgh/resources/` folder.

    [main]
    host = https://www.transifex.com

    [transifex-project.msg]
    file_filter = resources/l10n/msg_<lang>.properties
    source_file = resources/l10n/msg_en.properties
    source_lang = en
    type = UNICODEPROPERTIES

As these configuration files are the part of the compiled code so any changes require a redeployment of the app, think carefully in advance.

When this app is deployed to e.g. `http://drifted.in/txgh` then:
  * in Transifex project settings
      * set the hook URL to `http://drifted.in/txgh/transifex`
  * in GitHub project settings
      * set the webhook to `http://drifted.in/txgh/github` 
      * change the content type to `application/x-www-form-urlencoded`
      * set to process just push events


Dependencies
============
This Java app requires JDK 8, but it can be easily backported to JDK 7 if non JDK Base64 decoder is used. In this case just replace `Base64.getDecoder().decode()` with `DatatypeConverter.parseBase64Binary()` in `GitHubApi.getFileContent()` method.

All dependencies are specified in the Maven project file. This application relies especially on:
  * Eclipse EGit connector
  * Jersey RESTful client
  * INI4J for parsing INI files

It has been successfully tested on both Apache Tomcat 7 and 8.

Acknowledgment
==============
Big thanks belongs to guys behind the https://github.com/jsilland/txgh project. It was a huge source of inspiration!

