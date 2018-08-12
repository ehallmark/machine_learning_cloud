// run python code under gtt_models/src/java_compatibility/BuildPatentEncodings.py
cd ~/repos/gtt_models/
git pull origin master
. ~/environments/tfenv/bin/activate
python3 BuildPatentEncodings.py
cd ~/repos/machine_learning_cloud/