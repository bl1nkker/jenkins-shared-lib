def call(){
  pipeline{
    agent { label 'master' }

    options {
      buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
      booleanParam(
        name: 'DRY_RUN',
        defaultValue: true,
        description: 'If True, just prints the branches'
      )
    }

    stages {
      stage("Cleanup Branched") {
        steps {
          script {
            def pyScript = libraryResource('scripts/clean_branches.py')
            def requirements = libraryResource('requirements.txt')
            writeFile file: 'clean_branches.py', text: pyScript
            writeFile file: 'requirements.txt', text: requirements

            withCredentials([[
              $class: 'UsernamePasswordMultiBinding',
              credentialsId: 'GIT',
              usernameVariable: 'GIT_USER',
              passwordVariable: 'GIT_PASSWORD'
            ]]) {
              def dryRun = params.DRY_RUN ? '--dry-run' : ''
              sh """
                set -e
                python3 -m venv venv
                . venv/bin/activate
                pip install -r requirements.txt
                python3 clean_branches.py ${dryRun}
              """
            }
            echo "Cleanup job finished successfully"
          }
        }  
      }
    }
  }
}
