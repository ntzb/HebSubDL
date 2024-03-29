# HebSubDL

Supports:
1. Wizdom
2. Ktuvit (needs login information - see below)
3. OpenSubtitles (needs either login information, or a 'developer' useragent)

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
console.log(`Now paste it in the addons settings at the "Encrypted password field"`)
``` 
![Gujn9Y8vTF](https://user-images.githubusercontent.com/9304194/94992868-897f0100-0595-11eb-8694-0272ae2f19b9.gif)

#

~~for OpenSubtitles, you need a "developer" UserAgent: https://trac.opensubtitles.org/projects/opensubtitles/wiki/DevReadFirst#Howtorequestanewuseragent~~
~~no longer the case, you can login with both (developer useragent, or username(email)/password)~~
as of 2023, you have to to use opensubtitles.com API Key, in combination with your username (NOT email) and password, to automate the downloads.

#

Screenshots:

![s1](https://github.com/ntzb/HebSubDL/assets/1606302/5c81821a-b361-4a69-b856-dcba10f8fd47)
![s2](https://github.com/ntzb/HebSubDL/assets/1606302/57041c56-4eab-4f09-b38e-cb02fb57ad98)
![s3](https://github.com/ntzb/HebSubDL/assets/1606302/9935a42a-f98a-4cbd-a752-9a3f26bb5696)
![s4](https://github.com/ntzb/HebSubDL/assets/1606302/a93a10ce-ab77-466a-8e72-c0de5faad503)

#

Advanced config.properties features:
- `watch.directories`:  specify here comma separated paths (escape the backslash in Windows), to watch for changes, and when new file arrives, it will automatically add it to the queue
- `watch.ignorekeywords`:  specify here comma separated words, for the watch feature to ignore, in the filenames
- `log.level`:  specify here the wanted log level, i.e. `DEBUG`, if you want to submit issues

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


#
License:

For any part of this work for which the license is applicable, this work is licensed under the [Attribution-NonCommercial-NoDerivatives 4.0 International](http://creativecommons.org/licenses/by-nc-nd/4.0/) license. See LICENSE.CC-BY-NC-ND-4.0.

<a rel="license" href="http://creativecommons.org/licenses/by-nc-nd/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by-nc-nd/4.0/88x31.png" /></a>

Any part of this work for which the CC-BY-NC-ND-4.0 license is not applicable is licensed under the [Mozilla Public License 2.0](https://www.mozilla.org/en-US/MPL/2.0/). See LICENSE.MPL-2.0.

Any part of this work that is known to be derived from an existing work is licensed under the license of that existing work. Where such license is known, the license text is included in the LICENSE.ext file, where "ext" indicates the license.
