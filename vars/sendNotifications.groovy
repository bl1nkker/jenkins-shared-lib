import groovy.text.StreamingTemplateEngine

def call(){
  echo "About to send email..."
  def body = emailTemplate([
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

