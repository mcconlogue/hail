FROM {{ service_base_image.image }}

# bug in 0.4.0
# https://github.com/googleapis/google-auth-library-python-oauthlib/issues/46
# https://stackoverflow.com/questions/56555687/oauth-throws-missing-code-validator-in-google-oauth2
RUN pip3 install --upgrade google-auth-oauthlib==0.3.0

COPY auth/setup.py /auth/
COPY auth/auth /auth/auth/
RUN pip3 install --no-cache-dir /auth && \
  rm -rf /auth

EXPOSE 5000

CMD ["python3", "-m", "auth"]
