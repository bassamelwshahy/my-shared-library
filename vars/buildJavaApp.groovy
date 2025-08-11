def call(Map config = [:]) {
    new org.example.BuildJavaApp(this).run(config)
}
