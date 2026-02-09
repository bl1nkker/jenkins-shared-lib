import groovy.text.StreamingTemplateEngine

def call(){
  echo "--> Started: sending an email about build results"
  def statusColorMap = [
    'SUCCESS'  : 'green',
    'FAILURE'  : 'red',
    'NOT_BUILD' : 'red',
    'ABORTED'  : 'red'
  ]

  def body = emailTemplate([
    "STATUS_COLOR": statusColorMap.get(currentBuild.currentResult, 'goldenrod'),
    "INFO_TEXT": "${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME} ${currentBuild.currentResult}",
    "BUILD_URL": env.BUILD_URL,
    "BUILD_TIME": new Date(currentBuild.rawBuild.getStartTimeInMillis()).toString(),
    "BUILD_DURATION": currentBuild.rawBuild.getDurationString(),
    "ARTIFACTS": readArtifactsFromFile("artifacts.txt")
  ]);

  List mailRecipients = ['nurovich14@gmail.com',]
  emailext(
    body: body,
    mimeType: 'text/html',
    subject: '$DEFAULT_SUBJECT',
    to: mailRecipients.join(', '),
    recipientProviders: [
    //   requestor(),
    //   brokenBuildSuspects(),
    //   upstreamDevelopers(),
    ]
  )
  echo "--> Finished: sending an email about build results"
}

def readArtifactsFromFile(String fileName) {
    def result = []

    if (!fileExists(fileName)) {
        echo "Artifacts file '${fileName}' not found"
        return result
    }

    def mvnUrl = env.MVN_URL
    if (!mvnUrl) {
        error "MVN_URL is not defined"
    }

    if (mvnUrl.endsWith("/")) {
        mvn_url = mvn_url.substring(0, mvn_url.lastIndexOf("/"))
    }

    def baseUrl = mvnUrl.endsWith("/artifactory")
        ? "${mvnUrl}/maven-virtual"
        : "${mvnUrl}/content/repositories/public"

    readFile(fileName).readLines().each { line ->
        def artifact = line.trim()
        if (!artifact) return

        def lsgrp = artifact.split(":")
        if (lsgrp.size() < 3) return
        if (lsgrp.size() == 3) lsgrp += ["jar"]

        def artifactId = lsgrp[1]
        def version = lsgrp[2]
        def pkging = lsgrp[3]
        def classifier = lsgrp.size() > 4 ? lsgrp[4] : null

        def fileNameFinal = classifier
            ? "${artifactId}-${version}-${classifier}.${pkging}"
            : "${artifactId}-${version}.${pkging}"

        def url = [
            baseUrl,
            lsgrp[0].replace('.', '/'),
            artifactId,
            version,
            fileNameFinal
        ].join('/')

        result << [
            url       : url,
            short_name: fileNameFinal,
            full_name : artifact
        ]
    }

    return result
}


def emailTemplate(params) {
  echo "Preparing email template: ${params}"
  def fileName = "sample.template"
  def fileContents = libraryResource(fileName)
  def engine = new StreamingTemplateEngine()
  return engine.createTemplate(fileContents).make(params).toString()
}
