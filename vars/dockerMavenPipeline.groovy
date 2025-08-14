def call(Map config = [:]) {
    def pipelineRunner = new org.example.DockerMavenPipeline(this)
    pipelineRunner.runPipeline(
        config.imageName ?: "my-app",
        config.dockerCredsId ?: "dockerhub-creds",
        config.gitCredsId ?: "github-creds",
        config.gitEmail ?: "bassamelwshahy1@gmail.com",
        config.gitRepoUrl ?: "github.com/bassamelwshahy/argocd-nginx-demo.git"
    )
}
