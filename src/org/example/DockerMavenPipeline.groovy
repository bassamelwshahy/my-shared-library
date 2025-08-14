package org.example

class DockerMavenPipeline implements Serializable {

    def steps

    // Jenkins credentials IDs
    def dockerCredId = "dockerhub-creds" // Docker Hub credentials ID in Jenkins
    def githubId     = "github-cred"     // GitHub credentials ID in Jenkins

    DockerMavenPipeline(steps) {
        this.steps = steps
    }

    def runPipeline(String imageName, String dockerCredId, String githubId) {
        steps.node {
            steps.env.IMAGE_NAME = imageName

            // Stage 1: Checkout Code
            steps.stage('Checkout') {
                steps.checkout steps.scm
            }

            // Stage 2: Maven Build in Docker
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

            // Stage 3: Docker Build
            steps.stage('Docker Build') {
                def tag = "${steps.env.BUILD_NUMBER}"
                steps.sh """
                    docker build -t bassamelwshahy/${imageName}:${tag} \
                                 -t bassamelwshahy/${imageName}:latest .
                """
                steps.sh 'docker image prune -f || true'
            }

            // Stage 4: Docker Push
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

            // Stage 5: Update Deployment YAML in GitHub
           stage('Update Deployment YAML in GitHub') {
    def tag = "${env.BUILD_NUMBER}"
    
    withCredentials([usernamePassword(credentialsId: 'github-cred', // replace with your Jenkins credentials ID
                                      usernameVariable: 'GITHUB_CREDS_USR', 
                                      passwordVariable: 'GITHUB_CREDS_PSW')]) {
        sh """
            rm -rf argocd-nginx-demo
            git clone https://${GITHUB_CREDS_USR}:${GITHUB_CREDS_PSW}@github.com/bassamelwshahy/argocd-nginx-demo.git
            cd argocd-nginx-demo
            sed -i "s|image: .*|image: ${imageName}:${tag}|" deployment.yml
            
            git config user.email "jenkins@example.com"
            git config user.name "Jenkins CI"
            git add .
            git commit -m "Update image to ${imageName}:${tag}"
            git push origin HEAD:master
        """
    }
}
        }
    }
}
