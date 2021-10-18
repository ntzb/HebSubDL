# HebSubDL

Supports:
1. Wizdom
2. Ktuvit
3. OpenSubtitles

#

for Ktuvit - you need to use your email, and the encoded password as per these instructions (thanks [CaTz](https://github.com/XBMCil/service.subtitles.ktuvit)):
1. Open [ktuvit.me](https://www.ktuvit.me) in the browser
2. Open [developer tools](https://developers.google.com/web/tools/chrome-devtools/open)  (in Windows <kbd>ctrl</kbd> + <kbd>shift</kbd> + <kbd>c</kbd>
)
3. Enter this code in the **console, don't forget to change 'MY-PASSWORD' to your password, and 'MY@EMAIL.COM' to your email**: 
```javascript
x = { value: 'MY-PASSWORD' };
loginHandler.EncryptPassword({}, x, 'MY@EMAIL.COM');
copy(x.value); // this will copy your encrtyped password to your clipboard
console.log(`Now past it in the addon's settings at the Encrypted password field`)
``` 
![Gujn9Y8vTF](https://user-images.githubusercontent.com/9304194/94992868-897f0100-0595-11eb-8694-0272ae2f19b9.gif)

#

~~for OpenSubtitles, you need a "developer" UserAgent: https://trac.opensubtitles.org/projects/opensubtitles/wiki/DevReadFirst#Howtorequestanewuseragent~~
no longer the case, you can login with both (developer useragent, or username(email)/password

#

To Do:

- ~~work with Git, maven/gradle~~
- ~~recheck providers and fix as needed~~
- add database with simple table - file path, status (downloaded, not found, empty (not searched?)), ~~login cookie time validity~~
- ~~add log file with proper logger and logging levels~~
- ~~add directory watcher (https://docs.oracle.com/javase/tutorial/essential/io/notification.html)~~
- ~~add to config.properties: user/pass/api keys for providers, ignore keywords for scanner, file paths for scanner~~
- ~~minimize to tray~~
- (?) expose api to be called from kodi/others
- ~~use opensubtitles.com, or allow a username/password method instead of developer api method.~~

