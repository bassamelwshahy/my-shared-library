package org.example

class DockerMavenPipeline implements Serializable {
    def steps

    DockerMavenPipeline(steps) {
        this.steps = steps
    }

    def runPipeline(String imageName, String dockerCreds, String githubCreds) {
        steps.pipeline {
            agent any

            environment {
                IMAGE_NAME = imageName
                DOCKER_CREDS = dockerCreds
                GITHUB_CREDS = githubCreds
            }

            stages {

                // Stage 1: Checkout
                stage('Checkout') {
                    steps.checkout steps.scm
                }

                // Stage 2: Build with Maven
                stage('Build with Maven') {
                    steps.sh "docker run --rm -v \$(pwd):/workspace -w /workspace maven:3.9.5-eclipse-temurin-17 mvn -B clean package"
                }

                // Stage 3: Build & Push Docker Image
                stage('Build & Push Docker Image') {
                    steps.withCredentials([steps.usernamePassword(
                        credentialsId: dockerCreds,
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )]) {
                        def tag = "${steps.env.BUILD_NUMBER}"
                        steps.sh """
                            echo "${DOCKER_PASS}" | docker login -u "${DOCKER_USER}" --password-stdin
                            docker build -t ${imageName}:${tag} .
                            docker tag ${imageName}:${tag} ${DOCKER_USER}/${imageName}:${tag}
                            docker push ${DOCKER_USER}/${imageName}:${tag}
                        """
                    }
                }

                // Stage 4: Update Deployment YAML in GitHub
                stage('Update Deployment YAML in GitHub') {
                    def tag = "${steps.env.BUILD_NUMBER}"

                    steps.withCredentials([steps.usernamePassword(
                        credentialsId: githubCreds,
                        usernameVariable: 'GITHUB_CREDS_USR',
                        passwordVariable: 'GITHUB_CREDS_PSW'
                    )]) {
                        steps.sh """
                            rm -rf argocd-nginx-demo
                            git clone https://${GITHUB_CREDS_USR}:${GITHUB_CREDS_PSW}@github.com/${GITHUB_CREDS_USR}/argocd-nginx-demo.git
                            cd argocd-nginx-demo
                            sed -i "s|image: .*|image: ${imageName}:${tag}|" deployment.yml

                            git config user.email "jenkins@example.com"
                            git config user.name "Jenkins CI"
                            git add .
                            git commit -m "Update image to ${imageName}:${tag}" || echo "No changes to commit"
                            git push origin HEAD:master
                        """
                    }
                }
            }
        }
    }
}
