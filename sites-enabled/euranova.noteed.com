server {
  listen  80;
  server_name euranova.noteed.com;
  root /usr/share/nginx/www;
}
