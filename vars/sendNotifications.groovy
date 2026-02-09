import groovy.text.StreamingTemplateEngine

def call(){
  echo "About to send email..."
  def statusColorMap = [
    'SUCCESS'  : 'green',
    'FAILURE'  : 'red',
    'UNSTABLE' : 'goldenrod',
    'ABORTED'  : 'gray'
  ]

  def statusColor = statusColorMap.get(
      currentBuild.currentResult,
      'goldenrod' // fallback
  )
  def body = emailTemplate([
    "STATUS_COLOR": statusColor,
    "ARTIFACTS": readArtifactsFromFile("artifacts.txt"), 
    "BUILD_URL": env.BUILD_URL,
    "INFO_TEXT": "${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME} ${currentBuild.currentResult}",
    "BUILD_TIME": currentBuild.rawBuild.getTimestampString(),
    "BUILD_DURATION": currentBuild.rawBuild.getDurationString()
  ]);
  emailext(
    body: body,
    mimeType: 'text/html',
    subject: '$DEFAULT_SUBJECT',
    to: 'nurovich14@gmail.com',
    recipientProviders: []
  )
  echo "Email just sent."
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
        def artif = line.trim()
        if (!artif) return

        def lsgrp = artif.split(":")
        if (lsgrp.size() < 3) return
        if (lsgrp.size() == 3) lsgrp += ["jar"]

        def artifactId = lsgrp[1]
        def version    = lsgrp[2]
        def packaging  = lsgrp[3]
        def classifier = lsgrp.size() > 4 ? lsgrp[4] : null

        def fileNameFinal = classifier
            ? "${artifactId}-${version}-${classifier}.${packaging}"
            : "${artifactId}-${version}.${packaging}"

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
            full_name : artif
        ]
    }

    return result
}


def emailTemplate(params) {
  echo "Preparing email template: ${params}"
  def fileName = "sample.template"
  def fileContents = libraryResource(fileName)
  def engine = new StreamingTemplateEngine()
  echo "Preparation is done"
  return engine.createTemplate(fileContents).make(params).toString()
}

