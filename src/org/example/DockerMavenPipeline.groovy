package org.example

class DockerMavenPipeline implements Serializable {
    def steps

    DockerMavenPipeline(steps) {
        this.steps = steps
    }

    def runPipeline(String imageName, String credentialsId, String githubId) {
        steps.pipeline {
            agent {
                docker {
                    image 'maven:3.9.5-eclipse-temurin-17'
                    args '-v /root/.m2:/root/.m2'
                }
            }
            environment {
                GITHUB_CREDS = steps.credentials(githubId)
            }
            stages {
                stage('Checkout') {
                    steps {
                        checkout([$class: 'GitSCM',
                                  branches: [[name: "*/main"]],
                                  userRemoteConfigs: [[
                                      url: "https://github.com/bassamelwshahy/java.git",
                                      credentialsId: githubId
                                  ]]
                        ])
                    }
                }
                stage('Build') {
                    steps {
                        sh 'mvn -B clean package'
                    }
                }
                stage('Docker Build & Push') {
                    steps {
                        withCredentials([steps.usernamePassword(credentialsId: credentialsId,
                                                                usernameVariable: 'DOCKER_USER',
                                                                passwordVariable: 'DOCKER_PASS')]) {
                            sh """
                                echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                                docker build -t ${imageName}:latest .
                                docker push ${imageName}:latest
                            """
                        }
                    }
                }
            }
        }
    }
}
