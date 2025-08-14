package org.example

class DockerMavenPipeline implements Serializable {
    def steps

    DockerMavenPipeline(steps) {
        this.steps = steps
    }

    def runPipeline(String imageName, String dockerCreds, String githubCreds) {
        steps.node {
            
            // Stage 1: Checkout
            steps.stage('Checkout') {
                steps.checkout(steps.scm)
            }

            // Stage 2: Build with Maven
            steps.stage('Build with Maven') {
                steps.docker.image('maven:3.9.5-eclipse-temurin-17').inside {
                    steps.sh "mvn -B clean package"
                }
            }

            // Stage 3: Build Docker Image
            steps.stage('Build Docker Image') {
                steps.docker.withRegistry('', dockerCreds) {
                    steps.sh """
                        docker build -t ${imageName}:${steps.env.BUILD_NUMBER} .
                        docker push ${imageName}:${steps.env.BUILD_NUMBER}
                    """
                }
            }

            // Stage 4: Update Deployment YAML in GitHub
            steps.stage('Update Deployment YAML in GitHub') {
                def tag = "${steps.env.BUILD_NUMBER}"

                steps.withCredentials([steps.usernamePassword(
                    credentialsId: githubCreds,
                    usernameVariable: 'GITHUB_CREDS_USR',
                    passwordVariable: 'GITHUB_CREDS_PSW'
                )]) {
                    steps.sh """
                        rm -rf argocd-nginx-demo
                        git clone https://${steps.env.GITHUB_CREDS_USR}:${steps.env.GITHUB_CREDS_PSW}@github.com/${steps.env.GITHUB_CREDS_USR}/argocd-nginx-demo.git
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
