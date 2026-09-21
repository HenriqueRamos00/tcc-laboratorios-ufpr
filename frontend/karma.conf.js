// Config do karma existe por um motivo só: rodar a suíte dentro do contêiner.
//
// O Chromium recusa executar como root sem `--no-sandbox`, e rodar como
// usuário comum também não resolve — o contêiner nega a criação de namespace
// de usuário ("Operation not permitted"). Sem um launcher com o sandbox
// desligado, `ng test` compila a suíte e nunca a executa, que é pior do que
// não ter teste: o verde da compilação passa por verde de teste.
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: { jasmine: {}, clearContext: false },
    jasmineHtmlReporter: { suppressAll: true },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/portal-web'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }],
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['ChromeHeadlessSemSandbox'],
    customLaunchers: {
      ChromeHeadlessSemSandbox: {
        base: 'ChromeHeadless',
        // --disable-dev-shm-usage: o /dev/shm padrão do Docker tem 64 MB e o
        // Chromium trava ao estourá-lo durante a suíte.
        flags: ['--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage'],
      },
    },
    restartOnFileChange: true,
  });
};
