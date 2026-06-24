/*
 * Copyright (c) Juspay Technologies.
 *
 * This source code is licensed under the AGPL 3.0 license found in the
 * LICENSE file in the root directory of this source tree.
 */


#import <React/RCTBridgeModule.h>
#import <React/RCTBridge.h>
#import <React/RCTEventEmitter.h>
#import <HyperSDK/HyperSDK.h>
#import <React/RCTRootView.h>
#import <React/RCTViewManager.h>

@interface HyperSdkReact : RCTEventEmitter <RCTBridgeModule>

#import <HyperSDK/HyperSDK.h>
// Holds every HyperServices instance keyed by the merchant provided key ("default" when none).
@property (nonatomic, strong) NSMutableDictionary<NSString *, HyperServices *> *hyperInstances;
// Holds the HyperDelegate created for each instance so that merchant views stay alive per key.
@property (nonatomic, strong) NSMutableDictionary<NSString *, id<HyperDelegate>> *delegates;
// Returns the "default" instance. Kept for backward compatibility / fabric view.
+ (HyperServices*)getHyperInstance;
// Returns the instance registered for the given key ("default" when key is nil/empty).
+ (HyperServices*)getHyperInstanceForKey:(NSString *)key;
@end

@interface SdkDelegate : NSObject <HyperDelegate>
@property (nonatomic, strong) NSMutableDictionary *rootHolder;
@property (nonatomic, strong) NSMutableDictionary *heightHolder;
@property (nonatomic, strong) NSMutableDictionary *heightConstraintHolder;
@property (nonatomic, strong) RCTBridge *bridge;
- initWithBridge: (RCTBridge *) bridge;
@end

@interface SDKRootView : RCTRootView

@property (nonatomic, strong) NSLayoutConstraint *leading;
@property (nonatomic, strong) NSLayoutConstraint *trailing;

@end

@interface HyperFragmentViewManagerIOS : RCTViewManager
@end
