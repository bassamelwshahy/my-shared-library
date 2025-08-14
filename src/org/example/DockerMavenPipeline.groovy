package org.example

class DockerMavenPipeline implements Serializable {

    def steps

    // Hardcoded Jenkins credentials IDs (safe to hardcode IDs, NOT passwords)
    def dockerCredId = "docker-hub-cred"
    def githubId = "github-cred"

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
                 {
                    steps.sh """
                        rm -rf argocd-nginx-demo
                        git clone https://${usernameVariable}:${usernamePassword}@github.com/bassamelwshahy/argocd-nginx-demo.git
                        cd argocd-nginx-demo
                        sed -i "s|image: .*|image: ${imageName}:${tag}|" deployment.yml
                      git config user.email "jenkins@example.com"
            git config user.name "Jenkins CI"
            git remote set-url origin https://${GIT_USER}:${GIT_PASS}@github.com/bassamelwshahy/argocd-nginx-demo.git
            git add .
            git commit -m "Update image to ${imageName}:${tag} " 
            git push origin HEAD:master
                    """
                }
            }
        }
    }
}
