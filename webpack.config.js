const path = require("path");
const GoogleFontsPlugin = require("google-fonts-plugin").default;

module.exports = {
  context: path.join(__dirname, '/src/main/webapp/assets/common'),
  mode: 'development',
  entry: [
    './js/main.js',
    './js/gitbucket.js',
    './js/validation.js'
  ],
  //devtool: 'source-map',
  module: {
    rules: [
      {
        test: /\.css$/,
        use: [
          {loader: "style-loader"},
          {loader: "css-loader"}
        ]
      },
      {
        test: /(\.sass|\.scss)$/,
        use: [
          {loader: "style-loader"},
          {loader: "css-loader"},
          {loader: "sass-loader"}
        ]
      },
      {
        test: /\.svg(\?v=\d+\.\d+\.\d+)?$/,
        loader: "url-loader",
        options:{
          mimetype: "image/svg+xml",
          limit: 5 * 1024 * 1024
        }
      },
      {
        test: /\.(woff(\d+)?|ttf|eot)(\?v=\d+\.\d+\.\d+)?$/,
        loader: "url-loader",
        options:{
          mimetype: "application/font-woff",
          limit: 5 * 1024 * 1024
        }
      },
      {
        test: /\.(png|gif|jpg)$/,
        loader: "url-loader",
        options: {
          limit: 5 * 1024 * 1024
        }
      }
    ]
  },
  output: {
    path: path.join(__dirname, "/target/webapp/assets"),
    filename: "bundle.js"
  },
  plugins: [
    new GoogleFontsPlugin({
      fonts: [
        { family: "Source Sans Pro"}
      ],
      outputDir: "target/webapp/assets/fonts"
    })
  ]
}
