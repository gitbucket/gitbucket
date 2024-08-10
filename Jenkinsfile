pipeline {
    agent any

    stages {
        stage('Clone Repository') {
            steps {
                git url: 'https://github.com/gitbucket/gitbucket.git', branch: 'master'
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

    post {
        success {
            echo 'Build completed successfully!'
        }
        failure {
            echo 'Build failed!'
        }
    }
}
