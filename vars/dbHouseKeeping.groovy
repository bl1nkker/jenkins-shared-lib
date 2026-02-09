def call(){
  def pyScript = libraryResource("clear_table.py")
  withCredentials([[$class: 'UsernamePasswordMultiBinding',
        credentialsId: 'PSQL',
        usernameVariable: 'PSQL_MQ_USER',
        passwordVariable: 'PSQL_MQ_PASSWORD'
      ]]) {
    withEnv(['PYTHONHTTPVERIFY=0']){
      status = sh(script: pyScript, returnStatus: true)
    }
  }
}
