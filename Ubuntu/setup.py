from setuptools import setup, find_packages

setup(
    name="orb_slam3_localization",
    version="1.0.0",
    description="Android + Ubuntu ORB-SLAM3 Indoor Localization System",
    packages=find_packages(),
    python_requires=">=3.8",
    install_requires=[
        "numpy>=1.21.0",
        "opencv-python>=4.5.0",
        "pyyaml>=6.0",
        "scipy>=1.7.0",
    ],
)
