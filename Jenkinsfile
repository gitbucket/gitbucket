pipeline {
    agent any

    stages {
        stage('Clone Repository') {
            steps {
                git url: 'https://github.com/gitbucket/gitbucket.git', branch: 'master'
            }
        }
        
        stage('Run tests') {
            steps {
                sh 'sbt "testOnly * -- -l ExternalDBTest"'
            }
        }

        stage('Build') {
            steps {
                script {
                    sh 'sbt sbtVersion'
                    sh 'sbt package'
                }
            }
        }
    }
}
