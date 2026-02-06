import groovy.text.StreamingTemplateEngine

def call(){
  echo "About to send email..."
  def body = emailTemplate([
    "STATUS_COLOR": "red",
    "ARTIFACTS": [
      [url: "google.com", short_name: "name1", full_name: "full2"],
      [url: "google2.com", short_name: "name12", full_name: "full23"]
    ],
    "BUILD_URL": env.BUILD_URL,
    "INFO_TEXT": "${env.JOB_NAME} ${env.BUILD_DISPLAY_NAME} ${renameState(currentBuild.currentResult)}",
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


def emailTemplate(params) {
  echo "Preparing email template: ${params}"
  def fileName = "sample.template"
  def fileContents = libraryResource(fileName)
  def engine = new StreamingTemplateEngine()
  echo "Preparation is done"
  return engine.createTemplate(fileContents).make(params).toString()
}

