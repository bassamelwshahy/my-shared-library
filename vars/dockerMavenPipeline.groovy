def call(Map config = [:]) {
    def pipelineRunner = new org.example.DockerMavenPipeline(this)
    pipelineRunner.runPipeline(
        config.imageName ?: "default/image",
        config.credentialsId ?: "dockerhub-creds",
        config.githubId ?: "github-cred"
    )
}
