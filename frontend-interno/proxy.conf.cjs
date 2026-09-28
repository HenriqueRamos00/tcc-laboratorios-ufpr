module.exports = {
  '/api': {
    target: process.env.API_PROXY_TARGET || 'http://localhost:8080',
    secure: false,
    changeOrigin: true,
  },
};
