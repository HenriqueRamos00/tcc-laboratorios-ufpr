module.exports = {
  '/api/**': {
    target: process.env.API_PROXY_TARGET || 'http://localhost:8081',
    changeOrigin: true,
  },
};
