server {
  listen  80;
  server_name stormcase.noteed.com;
  root /usr/share/nginx/www;
}
