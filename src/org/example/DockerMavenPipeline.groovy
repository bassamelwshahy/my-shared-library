package org.example

class DockerMavenPipeline implements Serializable {

    def steps

    DockerMavenPipeline(steps) {
        this.steps = steps
    }

    def runPipeline(String imageName, String dockerCredsId, String gitCredsId, String gitEmail, String gitRepoUrl) {
        steps.node {
            steps.env.IMAGE_NAME = imageName

            steps.stage('Checkout') {
                steps.checkout steps.scm
            }

            steps.stage('Maven Build (in Docker)') {
                steps.sh "docker run --rm -v ${steps.env.WORKSPACE}:/workspace -w /workspace maven:3.9.5-eclipse-temurin-17 mvn -B clean package"
                steps.archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }

            steps.stage('Docker Build') {
                def tag = "${steps.env.BUILD_NUMBER}"
                steps.sh "docker build -t bassamelwshahy/${imageName}:${tag} -t bassamelwshahy/${imageName}:latest ."
                steps.sh 'docker image prune -f || true'
            }

            steps.stage('Docker Push') {
                steps.withCredentials([steps.usernamePassword(
                        credentialsId: dockerCredsId,
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                )]) {
                    steps.sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                    steps.sh "docker push bassamelwshahy/${imageName}:${steps.env.BUILD_NUMBER}"
                    steps.sh "docker push bassamelwshahy/${imageName}:latest"
                }
            }

            steps.stage('Update Manifests & Push to Git') {
                steps.withCredentials([steps.usernamePassword(
                    credentialsId: gitCredsId,
                    usernameVariable: 'GITHUB_USER',
                    passwordVariable: 'GITHUB_TOKEN'
                )]) {
                    steps.sh """
                        git checkout -B main
                        sed -i "s|image: .*|image: bassamelwshahy/my-app:${steps.env.BUILD_NUMBER}|" deployment.yaml
                        git config --global user.email "${gitEmail}"
                        git config --global user.name "Jenkins CI"
                        git add deployment.yaml
                        git commit -m "Update image tag to ${steps.env.BUILD_NUMBER}" || echo "No changes to commit"
                        git push https://${steps.env.GITHUB_USER}:${steps.env.GITHUB_TOKEN}@github.com/bassamelwshahy/argocd-nginx-demo.git HEAD:main
                    """
                }
            }
        }
    }
}
