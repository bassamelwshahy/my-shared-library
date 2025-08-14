package org.example

class DockerMavenPipeline implements Serializable {

    def steps

    DockerMavenPipeline(steps) {
        this.steps = steps
    }

    def runPipeline(String imageName, String dockerCredId, String githubId) {
        steps.node {
            steps.env.IMAGE_NAME = imageName

            steps.stage('Checkout') {
                steps.checkout steps.scm
            }

            steps.stage('Maven Build (in Docker)') {
                steps.sh """
                    docker run --rm \
                        -v ${steps.env.WORKSPACE}:/workspace \
                        -w /workspace \
                        maven:3.9.5-eclipse-temurin-17 \
                        mvn -B clean package
                """
                steps.archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }

            steps.stage('Docker Build') {
                def tag = "${steps.env.BUILD_NUMBER}"
                steps.sh """
                    docker build -t bassamelwshahy/${imageName}:${tag} \
                                 -t bassamelwshahy/${imageName}:latest .
                """
                steps.sh 'docker image prune -f || true'
            }

            steps.stage('Docker Push') {
                steps.withCredentials([steps.usernamePassword(
                    credentialsId: dockerCredId,
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    steps.sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                    steps.sh "docker push bassamelwshahy/${imageName}:${steps.env.BUILD_NUMBER}"
                    steps.sh "docker push bassamelwshahy/${imageName}:latest"
                }
            }

            steps.stage('Update Deployment YAML in GitHub') {
                def tag = "${steps.env.BUILD_NUMBER}"
                def manifestRepo = "https://github.com/bassamelwshahy/argocd-nginx-demo.git"
                def deploymentFile = "deployment.yaml" // adjust path as needed

                steps.dir('manifest-repo') {
                    steps.withCredentials([steps.usernamePassword(
                        credentialsId: githubId,
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_PASS'
                    )]) {
                        steps.sh """
                            git config --global user.email "jenkins@ci.com"
                            git config --global user.name "Jenkins CI"
                            git clone https://$GIT_USER:$GIT_PASS@${manifestRepo.replace('https://', '')} .
                            sed -i 's|image: .*|image: bassamelwshahy/${imageName}:${tag}|' ${deploymentFile}
                            git add ${deploymentFile}
                            git commit -m "Update image to ${imageName}:${tag}"
                            git push origin main
                        """
                    }
                }
            }
        }
    }
}
