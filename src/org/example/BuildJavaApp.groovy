package org.example

class BuildJavaApp implements Serializable {

    def script

    BuildJavaApp(script) {
        this.script = script
    }

    def run(Map config = [:]) {
        script.pipeline {
            agent any

            tools {
                maven config.get('mavenTool', 'mvn-3-5-4')
                jdk config.get('jdkTool', 'java-11')
            }

            environment {
                DOCKER_USER = script.credentials(config.get('dockerUserCred', 'docker-username'))
                DOCKER_PASS = script.credentials(config.get('dockerPassCred', 'docker-password'))
            }

            stages {
                stage("Dependency check") {
                    steps {
                        script.sh "mvn dependency-check:check"
                        script.dependencyCheckPublisher pattern: 'target/dependency-check-report.xml'
                    }
                }

                stage("Build app") {
                    steps {
                        script.sh "mvn clean package install"
                    }
                }

                stage("Archive app") {
                    steps {
                        script.archiveArtifacts artifacts: '**/*.jar', followSymlinks: false
                    }
                }

                stage("Docker build") {
                    steps {
                        script.sh "docker build -t ${config.get('dockerImage', 'myrepo/myapp')}:v${script.BUILD_NUMBER} ."
                        script.sh "docker images"
                    }
                }

                stage("Docker push") {
                    steps {
                        script.sh "echo $DOCKER_PASS | docker login -u $DOCKER_USER --password-stdin"
                        script.sh "docker push ${config.get('dockerImage', 'myrepo/myapp')}:v${script.BUILD_NUMBER}"
                    }
                }
            }
        }
    }
}
