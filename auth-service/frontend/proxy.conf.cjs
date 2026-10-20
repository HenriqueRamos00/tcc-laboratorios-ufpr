module.exports = {
  '/api': {
    target: process.env.API_PROXY_TARGET || 'http://localhost:9000',
    secure: false,
    changeOrigin: true,
  },
};
