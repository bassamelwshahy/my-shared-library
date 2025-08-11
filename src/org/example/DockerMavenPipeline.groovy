package org.example

class DockerMavenPipeline implements Serializable {

    def steps

    DockerMavenPipeline(steps) {
        this.steps = steps
    }

    def runPipeline(String imageName, String credentialsId) {
        steps.pipeline {
            agent any
            environment {
                IMAGE_NAME = imageName
            }
            options {
                steps.buildDiscarder(steps.logRotator(numToKeepStr: '10'))
                steps.timestamps()
            }
            stages {
                stage('Checkout') {
                    steps {
                        steps.checkout steps.scm
                    }
                }
                stage('Maven Build (in Docker)') {
                    steps {
                        steps.sh 'docker run --rm -v $WORKSPACE:/workspace -w /workspace maven:3.9.5-eclipse-temurin-17 mvn -B clean package'
                    }
                    post {
                        always {
                            steps.archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                        }
                    }
                }
                stage('Docker Build') {
                    steps {
                        steps.script {
                            def tag = "${steps.env.BUILD_NUMBER}"
                            steps.sh "docker build -t ${imageName}:${tag} -t ${imageName}:latest ."
                        }
                    }
                    post {
                        always {
                            steps.sh 'docker image prune -f || true'
                        }
                    }
                }
                stage('Docker Push') {
                    steps {
                        steps.withCredentials([steps.usernamePassword(credentialsId: credentialsId, usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                            steps.sh "echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin"
                            steps.sh "docker push ${imageName}:${steps.env.BUILD_NUMBER}"
                            steps.sh "docker push ${imageName}:latest"
                        }
                    }
                }
            }
        }
    }
}
