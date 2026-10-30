// Lets the development server answer app pages such as /event/<id> with the shell, as the server does.
if (config.devServer) {
    config.devServer.historyApiFallback = true;
}
