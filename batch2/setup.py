from setuptools import setup, find_packages

setup(
    name = 'batch',
    version = '0.0.2',
    url = 'https://github.com/hail-is/hail.git',
    author = 'Hail Team',
    author_email = 'hail@broadinstitute.org',
    description = 'GCE job manager',
    packages = find_packages(),
    include_package_data=True
)