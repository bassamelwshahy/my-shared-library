package org.example

class DockerMavenPipeline implements Serializable {

    def steps

    // Hardcoded Jenkins credentials IDs (safe to hardcode IDs, NOT passwords)
    def dockerCredId = "docker-hub-cred"
    def githubId = "github-cred"

    DockerMavenPipeline(steps) {
        this.steps = steps
    }

    def runPipeline(String imageName) {
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
                steps.withCredentials([steps.usernamePassword(
                    credentialsId: githubId,
                    usernameVariable: 'GIT_USER',
                    passwordVariable: 'GIT_PASS'
                )]) {
                    steps.sh """
                        git clone https://github.com/bassamelwshahy/argocd-nginx-demo.git
                        cd argocd-nginx-demo
                        sed -i "s|image: .*|image: ${imageName}:${tag}|"
                        git config user.email "ci@example.com"
                        git config user.name "Jenkins CI"
                        git commit -am "Update image to bassamelwshahy/${imageName}:${steps.env.BUILD_NUMBER}"
                        git push
                    """
                }
            }
        }
    }
}
