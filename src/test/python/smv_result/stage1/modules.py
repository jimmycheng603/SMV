#
# This file is licensed under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import smv

RESULT_VALS = [100, "100", 100.0]
RESULT_SCHEMA = "res0: Integer; res1: String; res2: Float"

class RM(smv.smvdataset.SmvResultModule, smv.SmvOutput):
    def requiresDS(self):
        return []

    def run(self, i):
        return [100, "100", 100.0]
