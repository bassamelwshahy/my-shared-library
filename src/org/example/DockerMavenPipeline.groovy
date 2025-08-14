package org.example

class DockerMavenPipeline implements Serializable {

    def steps

    DockerMavenPipeline(steps) {
        this.steps = steps
    }

    def runPipeline(String imageName, String credentialsId) {
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
                    credentialsId: credentialsId,
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    steps.sh "echo \$DOCKER_PASS | docker login -u \$DOCKER_USER --password-stdin"
                    steps.sh "docker push bassamelwshahy/${imageName}:${steps.env.BUILD_NUMBER}"
                    steps.sh "docker push bassamelwshahy/${imageName}:latest"
                }
            }
        }
    }
}
