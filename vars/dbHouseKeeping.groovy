def call(){
  def pyScript = libraryResource("clear_table.py")
  withCredentials([[$class: 'UsernamePasswordMultiBinding',
        credentialsId: 'PSQL',
        usernameVariable: 'PSQL_MQ_USER',
        passwordVariable: 'PSQL_MQ_PASSWORD'
      ]]) {
    echo "about to run pyScript"
    status = sh(script: pyScript, returnStatus: true)
    echo "run pyScript done"
  }
}
