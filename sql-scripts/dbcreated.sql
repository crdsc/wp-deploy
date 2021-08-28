CREATE DATABASE dummydb;

GRANT ALL PRIVILEGES ON dummydb.* TO "dummydbuser"@"%" IDENTIFIED BY "dummydbpass";

FLUSH PRIVILEGES;
