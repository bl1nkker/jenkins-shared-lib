def call() {
    pipeline {
        agent { label 'master' }

        options {
            buildDiscarder(logRotator(daysToKeepStr: '365', numToKeepStr: '1'))
            disableConcurrentBuilds()
            timestamps()
        }

        parameters {
            booleanParam(
                name: 'DRY_RUN',
                defaultValue: true,
                description: 'If true, script will not delete data from the table and only show what would be deleted'
            )
        }

        stages {
            stage('Cleanup MQ queue messages') {
                steps {
                    script {
                        // PSQL_MQ_URL should be defined in env so external script will use it implicitly
                        assert env.PSQL_MQ_URL

                        // Use Jenkins method to retrieve Library file and store it in workspace
                        def pyScript = libraryResource('scripts/cleanup_queue_messages.py')
                        def requirements = libraryResource('requirements.txt')

                        writeFile file: 'cleanup_queue_messages.py', text: pyScript
                        writeFile file: 'requirements.txt', text: requirements

                        withCredentials([[
                            $class: 'UsernamePasswordMultiBinding',
                            credentialsId: 'PSQL',
                            usernameVariable: 'PSQL_MQ_USER',
                            passwordVariable: 'PSQL_MQ_PASSWORD'
                        ]]) {

                            def dryRun = params.DRY_RUN ? '--dry-run' : ''
                            sh """
                                set -e
                                python3 -m venv venv
                                . venv/bin/activate
                                pip install -r requirements.txt
                                python3 cleanup_queue_messages.py ${dryRun}
                            """
                        }

                        echo "Cleanup job finished successfully"
                    }
                }
            }
        }
    }
}

