# Project differentiation and licenses
Note: `.` represents this repository's root directory.

## Project differentiation
Even though the transceiver framework and the MDP currently reside in the same Git repository (this will probably change in the future), their dependency is unidirectional (i.e., MDP depends on the transceiver framework, but not the other way around). While the transceiver framework serves as an application-agnostic framework for a wide range of possible applications, MDP is a concrete application merely using the transceiver framework as a library. This is why we consider those two to be two separate projects, each consisting of the following files:
    
* **Transceiver Framework** project:
    * All files and their respective contents *NOT* explicitly part of the MDP project (see below).
* **MDP** project: 
    * All files and their repective contents whose path contains the [`./mdp/`](./mdp/) as well as the [`./examples/mdp/`](./examples/mdp/) folder as prefix (i.e., all files in those two folders, in all of their respective subfolders, sub-subfolders, etc.).
    
## Licenses
### Transceiver Framework
For each file which is part of the **Transceiver Framework** project (as defined in the "Project differentiation" section): 
If not explicitly licensed otherwise (exceptions are listed in the section "Exceptions" below), the file and its contents are provided under the BSD 3-clause license. The full license text can be found in [`./licenses/LICENSE.BSD3-clause.md`](./licenses/LICENSE.BSD3-clause.md).

#### Exceptions
* For each license file in the [`./licenses/`](./licenses/) folder: The file and its contents are subject to their own respective terms and/or licenses (contact the corresponding copyright owners for more information).

#### 3rd party contributions
The **Transceiver Framework** project uses the following 3rd party contributions (libraries, packages, etc.):

| Name and link: | Used under license: |
| -------------- | ------------------- |
| [Apache Commons Math](http://commons.apache.org/proper/commons-math/) (version 3.6.1) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
| [Apache Commons Lang](http://commons.apache.org/proper/commons-lang/) (version 3.8) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
| [Apache Commons Collections](http://commons.apache.org/proper/commons-collections/) (version 4.2) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
| [Apache Commons IO](http://commons.apache.org/proper/commons-io/) (version 2.6) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
| [JSON.simple](https://code.google.com/archive/p/json-simple/) (version 1.1.1) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |

### MDP
For each file which is part of the **MDP** project (as defined in the "Project differentiation" section): 
If not explicitly licensed otherwise (exceptions are listed in the section "Exceptions" below), the file and its contents are provided under the GNU General Public License as published by the Free Software Foundation, either version 3 of the license, or (at your option) any later version. The full license text can be found in [`./licenses/LICENSE.GPL3.0.md`](./licenses/LICENSE.GPL3.0.md).

#### Exceptions
* For each license file in the [`./licenses/`](./licenses/) folder: The file and its contents are subject to their own respective terms and/or licenses (contact the corresponding copyright owners for more information).

#### 3rd party contributions
This project uses the following 3rd party contributions (libraries, packages, etc.):

| Name and link: | Used under license: |
| -------------- | ------------------- |
| [Transceiver Framework](https://github.com/GStepien/Transceiver_Framework) (version 1.0.0) | [BSD 3-clause License](./licenses/LICENSE.BSD3-clause.md) |
| [SDG](https://github.com/GStepien/SDG/) (version 1.0.0) | [GPL-3](https://github.com/GStepien/SDG/blob/master/licenses/LICENSE.GPL3.0.md) |
| [Rserve client](https://mvnrepository.com/artifact/org.rosuda.REngine/Rserve/1.8.1) (version 1.8.1) | [LGPL-2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html) |
| [Apache Commons Math](http://commons.apache.org/proper/commons-math/) (version 3.6.1) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
| [Apache Commons Lang](http://commons.apache.org/proper/commons-lang/) (version 3.8) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
| [Apache Commons Collections](http://commons.apache.org/proper/commons-collections/) (version 4.2) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
| [Apache Commons IO](http://commons.apache.org/proper/commons-io/) (version 2.6) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
| [JSON.simple](https://code.google.com/archive/p/json-simple/) (version 1.1.1) | [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) |
