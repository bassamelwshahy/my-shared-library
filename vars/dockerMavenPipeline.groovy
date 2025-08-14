def call(Map config = [:]) {
    def pipelineRunner = new org.example.DockerMavenPipeline(this)
    pipelineRunner.runPipeline(
        config.imageName ?: "my-app",            // Docker image name
        config.credentialsId ?: "dockerhub-creds", // DockerHub creds ID
        config.githubId ?: "github-cred"           // GitHub creds ID
    )
}
 
