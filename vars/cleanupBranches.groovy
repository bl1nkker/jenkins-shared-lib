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
        description: 'If True, script will not delete stale branches and only show what would be deleted'
      )
    }

    stages {
      stage("Collect branches to delete"){
        steps {
          script {
              def pyScript = libraryResource('scripts/collect_stale_branches.py')
              def utilsScript = libraryResource('scripts/utils.py')
              def requirements = libraryResource('requirements.txt')
              writeFile file: 'collect_stale_branches.py', text: pyScript
              writeFile file: 'utils.py', text: utilsScript
              writeFile file: 'requirements.txt', text: requirements

              withCredentials([[
                $class: 'UsernamePasswordMultiBinding',
                credentialsId: 'GIT',
                usernameVariable: 'GIT_USER',
                passwordVariable: 'GIT_PASSWORD'
              ]]) {
              sh """
                set -e
                python3 -m venv venv
                . venv/bin/activate
                pip install -r requirements.txt
                python3 collect_stale_branches.py
              """
            }
          }
        }
      }
      stage("Cleanup Branches") {
        steps {
          script {
            def pyScript = libraryResource('scripts/clean_stale_branches.py')
            def utilsScript = libraryResource('scripts/utils.py')
            def requirements = libraryResource('requirements.txt')
            writeFile file: 'clean_stale_branches.py', text: pyScript
            writeFile file: 'utils.py', text: utilsScript
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
                python3 clean_stale_branches.py ${dryRun}
              """
            }
          }
        }  
      }
    }
  }
}
